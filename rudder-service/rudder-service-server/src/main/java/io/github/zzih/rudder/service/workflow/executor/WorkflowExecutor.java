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

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.*;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.TriggerType;
import io.github.zzih.rudder.service.config.LogStorageService;
import io.github.zzih.rudder.service.registry.ServiceRegistryService;
import io.github.zzih.rudder.service.script.TaskDispatchService;
import io.github.zzih.rudder.service.workflow.WorkflowInstanceService;
import io.github.zzih.rudder.service.workflow.controlflow.subworkflow.SubWorkflowExecutor;
import io.github.zzih.rudder.service.workflow.executor.controlflow.ControlFlowTaskFactory;
import io.github.zzih.rudder.service.workflow.executor.dag.ResumeStateReconciler;
import io.github.zzih.rudder.service.workflow.executor.event.NodeCompletionEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流执行器入口。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理 Runner 线程池和 Runner 实例注册表</li>
 *   <li>轮询任务完成状态，转发 NodeCompletionEvent 到对应 Runner</li>
 *   <li>提供 cancel 接口</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor implements SubWorkflowExecutor {

    private final TaskDispatchService taskDispatchService;
    private final TaskDefinitionDao taskDefinitionDao;
    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ProjectDao projectDao;
    private final WorkflowInstanceDao workflowInstanceDao;
    private final WorkflowInstanceService workflowInstanceService;
    private final LogStorageService logService;
    private final ServiceRegistryService registryService;
    private final io.github.zzih.rudder.dao.dao.WorkspaceDao workspaceDao;
    private final TransactionTemplate txTemplate;
    private final TaskInstanceFactory taskInstanceFactory;
    private final ControlFlowTaskFactory controlFlowTaskFactory;
    private final ResumeStateReconciler resumeStateReconciler;

    @Value("${rudder.workflow.executor-threads:100}")
    private int executorThreads;

    /** Runner 线程池 */
    private ThreadPoolExecutor runnerPool;

    /** 任务完成轮询调度器 */
    private ScheduledExecutorService completionPoller;

    /** 活跃的 Runner 注册表：workflowInstanceId → runner */
    private final ConcurrentHashMap<Long, WorkflowInstanceRunner> activeRunners = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        runnerPool = new ThreadPoolExecutor(
                executorThreads, executorThreads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> new Thread(r, "wf-runner-" + Thread.currentThread().threadId()),
                new ThreadPoolExecutor.CallerRunsPolicy());
        runnerPool.allowCoreThreadTimeOut(true);

        // 兜底轮询：主路径走 RPC 回调，轮询降频到 30s 作为安全网
        completionPoller = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "wf-completion-poller"));
        completionPoller.scheduleWithFixedDelay(this::pollTaskCompletions, 10, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    void destroy() {
        completionPoller.shutdownNow();
        runnerPool.shutdownNow();
    }

    // ======================== 公共 API ========================

    /** 异步执行工作流实例 */
    public void execute(WorkflowInstance instance) {
        executeWithAncestors(instance, Set.of(), false);
    }

    /** 异步执行工作流实例(controller 路径用,内部 lookup entity 避免 controller 持 entity)。 */
    public void execute(Long instanceId) {
        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        execute(instance);
    }

    /**
     * HA 接管：调用方（{@code WorkflowOrphanService}）已经用 CAS 把 {@code owner_host} 写成本机，
     * 此处直接按 resume 模式重建 runner 并继续推进。
     */
    public void resume(WorkflowInstance instance) {
        executeWithAncestors(instance, Set.of(), true);
    }

    /** 同步执行并等待完成（子工作流用） */
    void executeAndWait(WorkflowInstance instance, Set<Long> ancestorCodes) throws InterruptedException {
        claimOwnership(instance);
        WorkflowInstanceRunner runner = createRunner(instance, ancestorCodes, false);
        activeRunners.put(instance.getId(), runner);
        runnerPool.submit(runner);
        runner.awaitCompletion();
        activeRunners.remove(instance.getId());
    }

    /** 取消工作流实例 */
    public void cancelInstance(Long instanceId) {
        WorkflowInstanceRunner runner = activeRunners.get(instanceId);
        if (runner != null) {
            runner.cancel();
        } else {
            // 没有活跃 runner，直接在 DB 标记取消
            workflowInstanceService.cancel(instanceId);
        }
    }

    @Override
    public List<Property> executeSubWorkflow(Long workflowDefinitionCode,
                                             Set<Long> ancestorWorkflowDefinitionCodes,
                                             List<Property> varPool) throws InterruptedException {
        WorkflowInstance subInstance = workflowInstanceService.createInstance(
                workflowDefinitionCode, TriggerType.MANUAL, varPool);
        executeAndWait(subInstance, ancestorWorkflowDefinitionCodes);

        WorkflowInstance finished = workflowInstanceDao.selectById(subInstance.getId());
        if (finished != null && finished.getStatus() == InstanceStatus.FAILED) {
            throw new RuntimeException("Sub-workflow " + workflowDefinitionCode + " failed");
        }
        if (finished == null || finished.getVarPool() == null) {
            return List.of();
        }
        // 子→父冒泡:按**子工作流 globalParams 中 Direct.OUT** 过滤产出。子工作流自己内部的
        // 上下游 OUT 可以随便设,但只有顶层声明 Direct.OUT 的才暴露给父工作流(对齐 DS SubWorkflow 行为)。
        List<Property> subVarPool = JsonUtils.toList(finished.getVarPool(), Property.class);
        return filterByOutSpec(subVarPool, workflowDefinitionCode);
    }

    /**
     * 按子工作流定义级 globalParams Direct.OUT 白名单过滤 varPool。
     * 没在 workflow_definition.global_params 里声明 OUT 的 prop 一律不暴露给父。
     */
    private List<Property> filterByOutSpec(List<Property> subVarPool, Long subWorkflowCode) {
        var workflowDef = workflowDefinitionDao.selectByCode(subWorkflowCode);
        if (workflowDef == null || workflowDef.getGlobalParams() == null
                || workflowDef.getGlobalParams().isBlank()) {
            return List.of();
        }
        List<Property> globalSpec = JsonUtils.toList(workflowDef.getGlobalParams(), Property.class);
        Set<String> declaredOutNames = new java.util.HashSet<>();
        for (Property p : globalSpec) {
            if (p != null && p.getProp() != null
                    && p.getDirect() == io.github.zzih.rudder.common.enums.datatype.Direct.OUT) {
                declaredOutNames.add(p.getProp());
            }
        }
        if (declaredOutNames.isEmpty()) {
            return List.of();
        }
        List<Property> filtered = new java.util.ArrayList<>();
        for (Property p : subVarPool) {
            if (p != null && declaredOutNames.contains(p.getProp())) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    /** RPC 回调入口：按 {@code workflowInstanceId} O(1) 路由到对应 runner。 */
    public void notifyTaskCompletion(NodeCompletionEvent event) {
        Long wfInstanceId = event.getWorkflowInstanceId();
        if (wfInstanceId == null) {
            log.warn("NodeCompletionEvent missing workflowInstanceId, dropped: taskInstanceId={}",
                    event.getTaskInstanceId());
            return;
        }
        WorkflowInstanceRunner runner = activeRunners.get(wfInstanceId);
        if (runner != null) {
            runner.notifyTaskCompletion(event);
        }
    }

    // ======================== 内部 ========================

    private void executeWithAncestors(WorkflowInstance instance, Set<Long> ancestorCodes, boolean resume) {
        if (!resume) {
            claimOwnership(instance);
        }
        WorkflowInstanceRunner runner = createRunner(instance, ancestorCodes, resume);
        activeRunners.put(instance.getId(), runner);
        runnerPool.submit(() -> {
            try {
                runner.run();
            } finally {
                activeRunners.remove(instance.getId());
            }
        });
    }

    /**
     * 登记该工作流由本 Server 持有。写入 {@code owner_host} 列，供 {@code WorkflowOrphanService}
     * 在本 Server 离线时识别孤儿。写入失败会让 reaper 永远看不到这行，属于数据面故障，需要上抛。
     */
    private void claimOwnership(WorkflowInstance instance) {
        String ownerHost = registryService.getLocalRpcAddress();
        if (ownerHost == null || ownerHost.isBlank()) {
            throw new IllegalStateException("Cannot claim ownership: local RPC address not available");
        }
        instance.setOwnerHost(ownerHost);
        workflowInstanceDao.updateById(instance);
    }

    private WorkflowInstanceRunner createRunner(WorkflowInstance instance, Set<Long> ancestorCodes, boolean resume) {
        String localHost = registryService.getLocalRpcAddress();
        String wsName = "default";
        if (instance.getWorkspaceId() != null) {
            var ws = workspaceDao.selectById(instance.getWorkspaceId());
            if (ws != null && ws.getName() != null) {
                wsName = ws.getName();
            }
        }
        RunnerDependencies deps = new RunnerDependencies(
                taskDispatchService,
                taskDefinitionDao,
                taskInstanceDao,
                workflowDefinitionDao,
                projectDao,
                workflowInstanceDao,
                this,
                logService,
                txTemplate,
                taskInstanceFactory,
                controlFlowTaskFactory,
                resumeStateReconciler);
        return new WorkflowInstanceRunner(instance, ancestorCodes, deps, wsName, localHost, resume);
    }

    /** RPC 回调失败时的兜底轮询，单线程。 */
    private void pollTaskCompletions() {
        try {
            for (var entry : activeRunners.entrySet()) {
                Long instanceId = entry.getKey();
                WorkflowInstanceRunner runner = entry.getValue();

                List<TaskInstance> tasks = taskInstanceDao.selectFinishedByWorkflowInstanceId(instanceId);
                for (TaskInstance task : tasks) {
                    if (task.getTaskDefinitionCode() != null) {
                        runner.notifyTaskCompletion(new NodeCompletionEvent(
                                task.getTaskDefinitionCode(),
                                task.getId(),
                                task.getStatus(),
                                task.getErrorMessage(),
                                instanceId,
                                null));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Completion polling error", e);
        }
    }
}
