/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.service.workflow.executor;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.placeholder.PlaceholderUtils;
import io.github.zzih.rudder.dao.entity.*;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.workflow.controlflow.*;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.executor.controlflow.ControlFlowContext;
import io.github.zzih.rudder.service.workflow.executor.dag.DagState;
import io.github.zzih.rudder.service.workflow.executor.dag.DagState.NodeState;
import io.github.zzih.rudder.service.workflow.executor.event.CompletionEventRouter;
import io.github.zzih.rudder.service.workflow.executor.event.NodeCompletionEvent;
import io.github.zzih.rudder.service.workflow.executor.varpool.VarPoolManager;
import io.github.zzih.rudder.task.api.log.TaskLogUtils;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 单个工作流实例的执行线程。
 * <p>所有 DAG 推进决策都在此线程内完成（单线程），彻底消除并发状态竞争。</p>
 * <p>
 * 主循环：
 * <ol>
 *   <li>找到所有就绪节点（上游全部完成）</li>
 *   <li>控制流节点 → 同步评估，产生 BranchDecision</li>
 *   <li>普通任务节点 → 投递到执行节点，阻塞等待完成事件</li>
 *   <li>处理完成事件，推进 DAG</li>
 * </ol>
 */
@Slf4j
public class WorkflowInstanceRunner implements Runnable {

    private static final String CANCEL_REASON_USER = "Cancelled by user";
    private static final String CANCEL_REASON_WORKFLOW = "Cancelled: workflow cancelled";

    private final WorkflowInstance instance;
    private final Set<Long> ancestorWorkflowDefinitionCodes;
    private final RunnerDependencies deps;
    private final String workspaceName;
    private final String localHost;

    /**
     * true = 从 DB 现有 task_instance 重建状态（HA 接管场景）；false = 从头执行（正常启动）。
     * 接管模式下不重建 DAG、不 rerun 已完成节点、不重置 varPool。
     */
    private final boolean resume;

    private final DagState dagState = new DagState();
    private final VarPoolManager varPool = new VarPoolManager();
    private final CompletionEventRouter eventRouter = new CompletionEventRouter();
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    /** 按 taskCode 缓存的 TaskDefinition；DAG 不可变、runner 专用线程读取，无需同步。 */
    private final Map<Long, TaskDefinition> taskDefCache = new HashMap<>();
    private volatile boolean cancelled = false;

    public WorkflowInstanceRunner(WorkflowInstance instance,
                                  Set<Long> ancestorWorkflowDefinitionCodes,
                                  RunnerDependencies deps,
                                  String workspaceName,
                                  String localHost,
                                  boolean resume) {
        this.instance = instance;
        this.ancestorWorkflowDefinitionCodes = new HashSet<>(ancestorWorkflowDefinitionCodes);
        this.ancestorWorkflowDefinitionCodes.add(instance.getWorkflowDefinitionCode());
        this.deps = deps;
        this.workspaceName = workspaceName;
        this.localHost = localHost;
        this.resume = resume;
    }

    /** 供外部取消 */
    public void cancel() {
        cancelled = true;
        eventRouter.offer(new NodeCompletionEvent(
                -1L, -1L, InstanceStatus.CANCELLED, CANCEL_REASON_USER, instance.getId(), null));
    }

    /** 阻塞等待工作流完成（子工作流用） */
    public void awaitCompletion() throws InterruptedException {
        completionLatch.await();
    }

    /** 供 {@link WorkflowExecutor} 的 RPC 回调 / 兜底轮询路由完成事件（线程安全，带去重）。 */
    public void notifyTaskCompletion(NodeCompletionEvent event) {
        eventRouter.offer(event);
    }

    @Override
    public void run() {
        try {
            initDag();
            if (dagState.isEmpty()) {
                finishInstance(InstanceStatus.SUCCESS);
                return;
            }
            mainLoop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Workflow instance {} runner interrupted", instance.getId());
            finishInstance(InstanceStatus.CANCELLED);
        } catch (Exception e) {
            log.error("Workflow instance {} runner failed", instance.getId(), e);
            finishInstance(InstanceStatus.FAILED);
        } finally {
            completionLatch.countDown();
        }
    }

    // ======================== 初始化 ========================

    private void initDag() {
        dagState.loadGraph(instance.getDagSnapshot());
        if (resume) {
            deps.resumeStateReconciler().reconcile(instance, dagState, varPool, eventRouter);
        } else {
            initDagFresh();
        }
    }

    private void initDagFresh() {
        WorkflowDefinition workflow = deps.workflowDefinitionDao().selectByCode(instance.getWorkflowDefinitionCode());
        String projectParamsJson = null;
        List<Property> workflowGlobalParams = List.of();
        if (workflow != null) {
            if (workflow.getProjectCode() != null) {
                Project project = deps.projectDao().selectByCode(workflow.getProjectCode());
                if (project != null) {
                    projectParamsJson = project.getParams();
                }
            }
            workflowGlobalParams = JsonUtils.toList(workflow.getGlobalParams(), Property.class);
        }
        if (workflowGlobalParams.isEmpty()) {
            workflowGlobalParams = dagState.graph().getGlobalParams();
        }
        // runtimeParams 在 WorkflowInstanceService.createInstance 入口已统一转成 List<Property> JSON,
        // VarPoolManager.init 三个参数槽都按 Property 列表语义消费。
        varPool.init(projectParamsJson, workflowGlobalParams, instance.getRuntimeParams());
        instance.setVarPool(varPool.toJson());
    }

    /** TaskDefinition lookup with per-runner cache — DAG 不变，定义在一次 wf 实例生命周期内稳定。 */
    private TaskDefinition taskDef(Long code) {
        return taskDefCache.computeIfAbsent(code, deps.taskDefinitionDao()::selectByCode);
    }

    // ======================== 主循环 ========================

    private void mainLoop() throws InterruptedException {
        while (true) {
            if (cancelled) {
                cancelAllRunning();
                finishInstance(InstanceStatus.CANCELLED);
                return;
            }

            // 级联 FAILED 逐节点交错处理（state 变更↔DB 写入），保留审计/监控观察到的顺序。
            DagState.ReadyScan scan = dagState.scanReady();
            for (Long failedCode : scan.newlyFailedByUpstream()) {
                dagState.set(failedCode, NodeState.FAILED);
                DagNode n = dagState.graph().getNodeByCode(failedCode);
                TaskDefinition def = taskDef(failedCode);
                if (n != null && def != null) {
                    deps.taskInstanceDao().insert(deps.taskInstanceFactory().buildUpstreamFailed(n, def, instance));
                }
            }
            for (Long taskCode : scan.ready()) {
                if (cancelled) {
                    break;
                }
                processNode(taskCode);
            }

            if (dagState.isAllDone()) {
                finishInstance(dagState.isAllSuccessOrSkipped() ? InstanceStatus.SUCCESS : InstanceStatus.FAILED);
                return;
            }

            if (dagState.hasSubmitted()) {
                NodeCompletionEvent event = eventRouter.poll(30, TimeUnit.SECONDS);
                if (event != null && event.getTaskCode() >= 0) {
                    handleCompletionEvent(event);
                }
            } else if (scan.ready().isEmpty()) {
                log.error("Workflow instance {} deadlocked: no ready or submitted nodes", instance.getId());
                finishInstance(InstanceStatus.FAILED);
                return;
            }
        }
    }

    // ======================== 节点处理 ========================

    private void processNode(Long taskCode) throws InterruptedException {
        DagNode node = dagState.graph().getNodeByCode(taskCode);
        TaskDefinition taskDef = taskDef(taskCode);
        if (taskDef == null) {
            log.error("TaskDefinition not found for code={} in instance {}", taskCode, instance.getId());
            dagState.set(taskCode, NodeState.FAILED);
            return;
        }

        // 禁用的节点直接跳过
        if (!Boolean.TRUE.equals(taskDef.getIsEnabled())) {
            deps.taskInstanceDao().insert(deps.taskInstanceFactory().buildSkipped(node, taskDef, instance));
            dagState.set(taskCode, NodeState.SKIPPED);
            return;
        }

        TaskType taskType = taskDef.getTaskType();
        if (taskType.isControlFlow()) {
            processControlFlowNode(node, taskDef, taskType);
        } else {
            processTaskNode(node, taskDef);
        }
    }

    // ---- 控制流节点：通过 Task 生命周期执行 ----

    private void processControlFlowNode(DagNode node, TaskDefinition taskDef,
                                        TaskType taskType) throws InterruptedException {
        TaskInstance controlTask = deps.taskInstanceFactory().buildForNode(
                node, taskDef, InstanceStatus.RUNNING, instance);
        controlTask.setStartedAt(LocalDateTime.now());
        // insert + logPath 必须原子：半成品行会让 fetchLog/fetchResult 静默返回空
        deps.txTemplate().executeWithoutResult(status -> {
            deps.taskInstanceDao().insert(controlTask);
            controlTask.setLogPath(deps.taskInstanceFactory().logPath(controlTask, instance, workspaceName));
            controlTask.setExecutionHost(localHost);
            deps.taskInstanceDao().updateById(controlTask);
        });

        deps.logService().ensureLogDir(controlTask.getLogPath());
        String localLogPath = deps.logService().toLocalPath(controlTask.getLogPath());

        List<Property> varSnapshot = varPool.snapshot();
        ControlFlowContext ctx = new ControlFlowContext(
                instance.getId(), varSnapshot, ancestorWorkflowDefinitionCodes, deps.subWorkflowExecutor());
        AbstractControlFlowTask task = deps.controlFlowTaskFactory().create(
                taskType, controlTask.getContent(), node, ctx);

        try (var ignored = TaskLogUtils.withTaskLog(controlTask.getId(), localLogPath)) {
            log.info("{} node {} ({}) started", taskType, node.getTaskCode(), node.getLabel());

            task.init();
            task.handle();

            BranchDecision decision = task.getBranchDecision();
            if (decision != null) {
                applyBranchDecision(node, decision);
                log.info("Branch decision: allowed={} skipped={}", decision.getAllowedNodes(),
                        decision.getSkippedNodes());
            }

            List<Property> outputs = task.getVarPool();
            if (outputs != null && !outputs.isEmpty()) {
                varPool.merge(outputs);
                instance.setVarPool(varPool.toJson());
                deps.workflowInstanceDao().updateById(instance);
                log.info("Output params merged: {}",
                        outputs.stream().map(Property::getProp).toList());
            }

            log.info("Status → SUCCESS");
            controlTask.setStatus(InstanceStatus.SUCCESS);
            controlTask.setFinishedAt(LocalDateTime.now());
            controlTask
                    .setDuration(Duration.between(controlTask.getStartedAt(), controlTask.getFinishedAt()).toMillis());
            deps.taskInstanceDao().updateById(controlTask);
            dagState.set(node.getTaskCode(), NodeState.SUCCESS);

        } catch (Exception e) {
            log.error("Status → FAILED: {}", e.getMessage());
            controlTask.setStatus(InstanceStatus.FAILED);
            controlTask.setErrorMessage(e.getMessage());
            controlTask.setFinishedAt(LocalDateTime.now());
            controlTask
                    .setDuration(Duration.between(controlTask.getStartedAt(), controlTask.getFinishedAt()).toMillis());
            deps.taskInstanceDao().updateById(controlTask);
            dagState.set(node.getTaskCode(), NodeState.FAILED);
        }

        // 上传日志到 FileStorage
        deps.logService().uploadLog(controlTask.getLogPath());
    }

    /** 应用分支决策：标记被跳过的分支及其独占下游 */
    private void applyBranchDecision(DagNode conditionNode, BranchDecision decision) {
        for (Long skippedCode : decision.getSkippedNodes()) {
            skipNodeAndExclusiveDownstream(skippedCode);
        }
    }

    /**
     * 跳过一个节点及其"独占下游"。
     * 独占下游：一个节点的所有下游中，如果某个下游的全部上游都已是 SKIPPED，则也跳过。
     * 单线程 BFS，不会有汇合节点竞争问题。
     */
    private void skipNodeAndExclusiveDownstream(Long startCode) {
        Queue<Long> queue = new LinkedList<>();
        queue.add(startCode);

        while (!queue.isEmpty()) {
            Long code = queue.poll();
            if (dagState.get(code) != NodeState.WAITING) {
                continue;
            }

            // 汇合节点检查：如果有上游不是 SKIPPED，不跳过
            List<Long> upstreams = dagState.graph().getUpstreamNodeCodes(code);
            boolean allUpstreamSkipped = upstreams.stream().allMatch(u -> dagState.get(u) == NodeState.SKIPPED);
            if (!allUpstreamSkipped && !code.equals(startCode)) {
                continue; // 起始节点无条件跳过
            }

            dagState.set(code, NodeState.SKIPPED);
            DagNode dagNode = dagState.graph().getNodeByCode(code);
            TaskDefinition def = taskDef(code);
            if (dagNode != null && def != null) {
                deps.taskInstanceDao().insert(deps.taskInstanceFactory().buildSkipped(dagNode, def, instance));
            }

            // 把下游加入队列继续检查
            for (Long downstream : dagState.graph().getDownstreamNodeCodes(code)) {
                if (dagState.get(downstream) == NodeState.WAITING) {
                    queue.add(downstream);
                }
            }
        }
    }

    // ---- 普通任务节点：投递到执行节点 ----

    private void processTaskNode(DagNode node, TaskDefinition taskDef) {
        TaskInstance execution = deps.taskInstanceFactory().buildForNode(
                node, taskDef, InstanceStatus.PENDING, instance);

        // 节点入参装配 — 对齐 DS 3.4.1 paramParsingPreparation 行为:
        // 整张 varPool(承载 project / global / runtime / 上游 OUT)整体灌进 nodeParams,
        // 节点 task_definition.input_params 里 Direct.IN 声明仅为 varPool 没有的 prop 提供 default。
        //
        // DS step 6 的限制是"varPool 只覆盖已声明 IN 的 value",而不是"必须声明 IN 才能拿到 prop"。
        // 因此节点没声明 ${env} 也能从 globalParams 拿到值 — 这是 DS 的 user-visible 行为,
        // Rudder 必须对齐,否则迁移过来的 SQL/脚本里引用 globalParams 的 ${var} 全挂掉。
        //
        // 单 prop 优先级(低 → 高):
        // 1. local IN 声明的 default(占位符用 varPool 解析过一次,只在 varPool 无同名时生效)
        // 2. varPool 同名值(project/global/runtime/上游 OUT last-wins 合并结果)
        Map<String, Property> nodeParams = new LinkedHashMap<>();
        Map<String, String> resolveMap = new LinkedHashMap<>();
        varPool.fillNodeParamsAndResolveMap(nodeParams, resolveMap);
        for (Property declared : JsonUtils.toList(taskDef.getInputParams(), Property.class)) {
            if (declared.getDirect() != Direct.IN
                    || declared.getProp() == null
                    || declared.getProp().isBlank()) {
                continue;
            }
            // varPool 已有同名值:保留 varPool 值(DS 行为 — 上游 OUT/global/runtime 优先于 local default)
            if (nodeParams.containsKey(declared.getProp())) {
                continue;
            }
            // varPool 无同名:用 local IN 声明的 default value
            String resolved = PlaceholderUtils.replacePlaceholders(
                    declared.getValue() != null ? declared.getValue() : "", resolveMap, true);
            nodeParams.put(declared.getProp(), Property.builder()
                    .prop(declared.getProp())
                    .direct(Direct.IN)
                    .type(declared.getType() != null ? declared.getType() : DataType.VARCHAR)
                    .value(resolved)
                    .build());
        }
        if (!nodeParams.isEmpty()) {
            execution.setParams(JsonUtils.toJson(new ArrayList<>(nodeParams.values())));
        }

        // insert + logPath 必须原子：半成品行会让 fetchLog/fetchResult 静默返回空
        deps.txTemplate().executeWithoutResult(status -> {
            deps.taskInstanceDao().insert(execution);
            execution.setLogPath(deps.taskInstanceFactory().logPath(execution, instance, workspaceName));
            deps.taskInstanceDao().updateById(execution);
        });

        // RPC dispatch 刻意排除在事务外：长 RPC 会握住 DB 行锁数分钟
        try {
            String executionHost = deps.taskDispatchService().dispatch(execution.getId());
            execution.setExecutionHost(executionHost);
            deps.taskInstanceDao().updateById(execution);
            dagState.set(node.getTaskCode(), NodeState.SUBMITTED);
            log.info("Task node {} dispatched to {} in instance {}", node.getTaskCode(), executionHost,
                    instance.getId());
        } catch (Exception e) {
            execution.setStatus(InstanceStatus.FAILED);
            execution.setErrorMessage("Dispatch failed: " + e.getMessage());
            execution.setFinishedAt(LocalDateTime.now());
            deps.taskInstanceDao().updateById(execution);
            dagState.set(node.getTaskCode(), NodeState.FAILED);
            log.error("Failed to dispatch task node {} in instance {}", node.getTaskCode(), instance.getId(), e);
        }
    }

    // ======================== 完成事件处理 ========================

    private void handleCompletionEvent(NodeCompletionEvent event) {
        Long taskCode = event.getTaskCode();
        NodeState current = dagState.get(taskCode);
        if (current == null) {
            return;
        }
        // 防御：节点已经终态则忽略重复事件（即使 notifyTaskCompletion 去重失效也不会重复处理）
        if (current == NodeState.SUCCESS || current == NodeState.FAILED || current == NodeState.SKIPPED) {
            return;
        }

        if (event.getStatus() == InstanceStatus.SUCCESS) {
            dagState.set(taskCode, NodeState.SUCCESS);
            if (mergeTaskOutputs(event, taskCode)) {
                instance.setVarPool(varPool.toJson());
                deps.workflowInstanceDao().updateById(instance);
            }
        } else {
            dagState.set(taskCode, NodeState.FAILED);
        }
    }

    /**
     * 合并任务输出到 varPool。返回 true 表示 varPool 实际被修改。
     *
     * <p>RPC 回调路径：event 自带 {@code varPoolJson}（Execution 端 TaskPipeline 结束时回传），
     * 省一次 SELECT。兜底轮询退化到从 DB 补读。{@code outputParams} 是 task_definition 上的流程配置，
     * 不在事件里。
     */
    private boolean mergeTaskOutputs(NodeCompletionEvent event, Long taskCode) {
        boolean changed = false;
        String varPoolJson = event.getVarPoolJson();
        if (varPoolJson == null || varPoolJson.isBlank()) {
            TaskInstance latest = deps.taskInstanceDao().selectById(event.getTaskInstanceId());
            if (latest != null) {
                varPoolJson = latest.getVarPool();
            }
        }
        // task_instance.var_pool 持的是 List<Property> JSON(全部 Direct.OUT,Worker 写入时强制)
        List<Property> outputs = JsonUtils.toList(varPoolJson, Property.class);
        if (!outputs.isEmpty()) {
            varPool.merge(outputs);
            changed = true;
        }

        // task_definition.output_params 是流程级 OUT 配置:可以引用 varPool 里其他变量,
        // 在这里现解析出最终值再 merge。例:{prop:report_path, value:'/data/${biz_date}.csv'}
        TaskDefinition def = taskDef(taskCode);
        if (def != null) {
            List<Property> resolved = new ArrayList<>();
            Map<String, String> snapshotForResolve = varPool.resolveMap();
            for (Property p : JsonUtils.toList(def.getOutputParams(), Property.class)) {
                if (p.getDirect() == Direct.OUT && p.getProp() != null) {
                    String val = PlaceholderUtils.replacePlaceholders(
                            p.getValue() != null ? p.getValue() : "", snapshotForResolve, true);
                    resolved.add(Property.builder()
                            .prop(p.getProp())
                            .direct(Direct.OUT)
                            .type(p.getType() != null ? p.getType() : DataType.VARCHAR)
                            .value(val)
                            .build());
                }
            }
            if (!resolved.isEmpty()) {
                varPool.merge(resolved);
                changed = true;
            }
        }
        return changed;
    }

    // ======================== 工具方法 ========================

    private void cancelAllRunning() {
        // 每个 live 行单独做 RPC（cancelOnExecution 是每任务一跳），
        // DB 侧用一次 bulk UPDATE 把状态/error_message/finished_at 一并 flip，省掉 N 次 updateById。
        for (TaskInstance task : deps.taskInstanceDao().selectRunningByWorkflowInstanceId(instance.getId())) {
            if (task.getExecutionHost() != null) {
                try {
                    deps.taskDispatchService().cancelOnExecution(task.getExecutionHost(), task.getId());
                } catch (Exception e) {
                    // worker 不可达时仍要继续 cancel 其余任务;但要 log 否则进程残留无人发现
                    log.warn("cancelOnExecution failed for task {} on host {}: {}",
                            task.getId(), task.getExecutionHost(), e.getMessage());
                }
            }
        }
        deps.taskInstanceDao().cancelPendingAndRunningByInstanceId(instance.getId(), CANCEL_REASON_WORKFLOW);

        for (Map.Entry<Long, NodeState> entry : dagState.snapshot().entrySet()) {
            if (entry.getValue() == NodeState.SUBMITTED) {
                dagState.set(entry.getKey(), NodeState.FAILED);
            }
        }
    }

    private void finishInstance(InstanceStatus status) {
        try {
            WorkflowInstance inst = deps.workflowInstanceDao().selectById(instance.getId());
            if (inst != null && !inst.getStatus().isFinished()) {
                inst.setStatus(status);
                inst.setFinishedAt(LocalDateTime.now());
                inst.setVarPool(varPool.toJson());
                deps.workflowInstanceDao().updateById(inst);
                log.info("Workflow instance {} finished with status {}", instance.getId(), status);
            }
        } catch (Exception e) {
            log.error("Failed to finalize workflow instance {}", instance.getId(), e);
        }
    }
}
