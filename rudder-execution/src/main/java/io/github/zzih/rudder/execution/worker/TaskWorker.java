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

package io.github.zzih.rudder.execution.worker;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.ExceptionFormatter;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.execution.TaskCompletionReport;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.placeholder.BuiltInParams;
import io.github.zzih.rudder.common.utils.placeholder.ParameterResolver;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.RuntimeType;
import io.github.zzih.rudder.execution.worker.injector.InjectionContext;
import io.github.zzih.rudder.execution.worker.pipeline.TaskPipeline;
import io.github.zzih.rudder.rpc.client.Clients;
import io.github.zzih.rudder.rpc.client.RpcClient;
import io.github.zzih.rudder.rpc.service.ITaskCallbackService;
import io.github.zzih.rudder.runtime.api.TaskFactory;
import io.github.zzih.rudder.service.config.LogStorageService;
import io.github.zzih.rudder.service.config.ResultConfigService;
import io.github.zzih.rudder.service.config.RuntimeConfigService;
import io.github.zzih.rudder.service.script.TaskInstanceService;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.log.TaskLogUtils;
import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.plugin.TaskPluginManager;
import io.github.zzih.rudder.task.api.spi.TaskChannel;
import io.github.zzih.rudder.task.api.task.*;
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行工作节点。通过 HTTP 接收任务并执行。
 * 使用 {@link TaskPipeline} 进行自动资源注入和结果收集，
 * 消除 instanceof 链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWorker {

    private final TaskInstanceService taskInstanceService;
    private final TaskPluginManager taskPluginManager;
    private final TaskPipeline pipeline;
    private final ResourceResolver resourceResolver;
    private final RpcClient rpcClient;
    private final LogStorageService logService;
    private final RuntimeConfigService runtimeConfigService;
    private final ResultConfigService resultConfigService;

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);
    private static final String CANCELLED_BY_USER = "Cancelled by user";

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            r -> {
                Thread t = new Thread(r, "task-worker-" + THREAD_SEQ.getAndIncrement());
                t.setDaemon(false);
                return t;
            });

    /** 以 taskInstanceId 为键的运行中任务，用于支持取消操作。 */
    private final ConcurrentHashMap<Long, Task> runningTasks = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down TaskWorker executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in 30s, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 任务对应的回调地址：taskInstanceId → Server RPC host:port */
    private final ConcurrentHashMap<Long, String> callbackAddresses = new ConcurrentHashMap<>();

    public void submitTask(Long taskInstanceId, String callbackAddress) {
        if (callbackAddress != null) {
            callbackAddresses.put(taskInstanceId, callbackAddress);
        }
        // FutureTask 静默吞 Throwable,显式 catch 保证 Error 也落日志。
        executor.submit(() -> {
            try {
                executeTask(taskInstanceId);
            } catch (Throwable t) {
                log.error("Unhandled throwable in task worker (taskInstanceId={})", taskInstanceId, t);
            }
        });
    }

    public void cancelTask(Long taskInstanceId) {
        executor.submit(() -> doCancelTask(taskInstanceId));
    }

    public String triggerSavepoint(Long taskInstanceId) {
        // Savepoint 能力暂未在重构后的 Task 模型中实现 — Flink streaming 的 cancel/savepoint
        // 由前置 cancel + 用户手动指定 savepointPath 重启替代。保留 RPC 入口以避免 Server 端调用失败。
        throw new UnsupportedOperationException(
                "Savepoint not supported for task " + taskInstanceId + " in current task model");
    }

    private void doCancelTask(Long taskInstanceId) {
        Task task = runningTasks.remove(taskInstanceId);
        if (task != null) {
            try {
                task.cancel();
                log.info("Task {} cancelled via task.cancel()", taskInstanceId);
            } catch (Exception e) {
                log.warn("Failed to cancel task {}: {}", taskInstanceId, e.getMessage());
            }
        }
        TaskInstance instance = taskInstanceService.findByIdInternal(taskInstanceId);
        if (instance != null && !instance.getStatus().isFinished()) {
            markFinished(instance, InstanceStatus.CANCELLED, CANCELLED_BY_USER);
        }
    }

    /** 给 instance 打终态:统一 set status/finishedAt/errorMessage/duration + flush 到 DB。 */
    private void markFinished(TaskInstance instance, InstanceStatus status, String errorMessage) {
        instance.setStatus(status);
        instance.setFinishedAt(LocalDateTime.now());
        instance.setErrorMessage(errorMessage);
        if (instance.getStartedAt() != null) {
            instance.setDuration(Duration.between(instance.getStartedAt(), instance.getFinishedAt()).toMillis());
        }
        taskInstanceService.updateInternal(instance);
    }

    private void executeTask(Long taskInstanceId) {
        // 幂等 CAS：仅当 status=PENDING 时才转 RUNNING，避免 RPC 重试/双派发导致同一实例被跑两次
        LocalDateTime startedAt = LocalDateTime.now();
        RuntimeType rtType = RuntimeType.fromValue(runtimeConfigService.activeProvider());
        int claimed = taskInstanceService.claimPending(taskInstanceId, rtType, startedAt);
        if (claimed == 0) {
            log.warn("Task instance {} is not PENDING (already claimed or finished), skipping", taskInstanceId);
            callbackAddresses.remove(taskInstanceId);
            return;
        }

        TaskInstance instance = taskInstanceService.findByIdInternal(taskInstanceId);
        String storageLogPath = instance.getLogPath();
        logService.ensureLogDir(storageLogPath);
        String localLogPath = logService.toLocalPath(storageLogPath);

        Task task = null;
        String executePath = null;

        try (var ignored = TaskLogUtils.withTaskLog(taskInstanceId, localLogPath)) {
            try {
                // 参数替换:instance.params 持 List<Property> JSON,拆成 prop→value 喂 ParameterResolver。
                String originalContent = instance.getContent();
                List<Property> instanceProps = JsonUtils.toList(instance.getParams(), Property.class);
                Map<String, String> paramMap = new HashMap<>();
                for (Property p : instanceProps) {
                    if (p.getProp() != null && p.getValue() != null) {
                        paramMap.put(p.getProp(), p.getValue());
                    }
                }

                // 内置参数 (system.*) 上下文构建一次,paramMap 第一次解析(executePath=null)用,
                // 后面 prepareParams 装填(executePath 解析后)再用一次 — 避免两次 DAO 查 wf/project。
                BuiltInParams.BuiltInContext builtInCtx =
                        taskInstanceService.buildBuiltInContext(instance, null);
                // putIfAbsent: built-in 优先级最低,用户 project/global/runtime/local 同名时覆盖之
                BuiltInParams.build(builtInCtx).forEach(paramMap::putIfAbsent);

                if (!paramMap.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Parameters → {");
                    paramMap.forEach((k, v) -> sb.append(k).append('=').append(v).append(", "));
                    sb.setLength(sb.length() - 2);
                    sb.append('}');
                    log.info("{}", sb);
                }

                // baseTime 用 instance.startedAt 跟 BuiltInParams 一致 — 时间表达式 $[yyyyMMdd]
                // 算出来的日期跟 system.biz.curdate 必须等价。
                Date baseTime = instance.getStartedAt() != null
                        ? Date.from(instance.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                        : new Date();
                // SQL 类任务:Worker 只解析 $[time],${var}/!{var} 留给 SqlExecutor 走 PreparedStatement
                // (防注入 + LIST 展开 + 表名类原值替换)。其他类任务(Shell/Python/Jar)继续字符串
                // 替换 — 它们的脚本里 ${var} 没有 prepared 概念,只能直接拼。
                String resolvedContent = instance.getTaskType() != null && instance.getTaskType().isSql()
                        ? ParameterResolver.resolveTimeOnly(originalContent, baseTime)
                        : ParameterResolver.resolveAll(originalContent, paramMap, baseTime);

                // 未解析的 ${...} 占位符保留原样（和 DolphinScheduler 行为一致）
                // 不清空：Shell/Python 脚本里 ${var} 是合法语法，SQL 里未解析的占位符也应保留让用户看到

                instance.setContent(resolvedContent);

                String paramsJson = buildParamsJson(instance);

                TaskChannel channel = taskPluginManager.getChannel(instance.getTaskType());
                Map<String, String> resolvedFilePaths = null;
                AbstractTaskParams taskParams = channel.parseParams(paramsJson);
                if (taskParams instanceof SqlTaskParams sqlParams) {
                    sqlParams.setQueryLimit(resultConfigService.getDefaultQueryRows());
                }

                // Layer 1 — 通用 params 结构 dump,所有 task 都会打这一条;
                // Layer 2 (raw 脚本 / SQL / 命令行) 由各 Task 自己在 handle() 打。
                log.info("Initialize {} task params:\n{}",
                        instance.getTaskType().getLabel(), JsonUtils.toPrettyJson(taskParams));

                if (taskParams != null && !taskParams.validate()) {
                    throw new TaskException(TaskErrorCode.TASK_PARAM_INVALID,
                            instance.getTaskType().getLabel());
                }

                Map<String, String> resourcePaths = taskParams != null ? taskParams.getResourceFiles() : Map.of();
                if (!resourcePaths.isEmpty()) {
                    ResourceResolver.ResolveResult result = resourceResolver.resolveResources(
                            instance.getWorkflowInstanceId(), taskInstanceId, resourcePaths);
                    executePath = result.executePath();
                    resolvedFilePaths = result.resolvedFilePaths();
                    log.info("Resolved {} resource(s) to {}", resolvedFilePaths.size(), executePath);
                }

                // timeout 从 TaskDefinition 获取（通过 task_definition_code 查询）
                int timeoutSeconds = 0;
                Integer timeoutMinutes =
                        taskInstanceService.findTaskDefinitionTimeoutMinutes(instance.getTaskDefinitionCode());
                if (timeoutMinutes != null && timeoutMinutes > 0) {
                    timeoutSeconds = timeoutMinutes * 60;
                }
                // executionMode 从 content JSON 提取
                String executionMode = ExecutionMode.BATCH.name();
                if (instance.getContent() != null && !instance.getContent().isBlank()) {
                    try {
                        Map<String, Object> contentMap = JsonUtils.fromJson(
                                instance.getContent(), new TypeReference<>() {
                                });
                        Object modeObj = contentMap.get("executionMode");
                        if (modeObj != null) {
                            executionMode = modeObj.toString();
                        }
                    } catch (Exception e) {
                        /* ignore */ }
                }

                // PreparedStatement 路径用的 prepareParams:instanceProps + built-in。
                // 复用上面已经查过的 builtInCtx,只更新 executePath(资源解析后才有)— 省一次 DAO 链。
                List<Property> prepareParams = new ArrayList<>(instanceProps);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Property p : instanceProps) {
                    if (p.getProp() != null) {
                        seen.add(p.getProp());
                    }
                }
                builtInCtx.executePath(executePath);
                for (Map.Entry<String, String> e : BuiltInParams.build(builtInCtx).entrySet()) {
                    if (!seen.contains(e.getKey())) {
                        prepareParams.add(Property.builder()
                                .prop(e.getKey())
                                .direct(Direct.IN)
                                .type(DataType.VARCHAR)
                                .value(e.getValue())
                                .build());
                    }
                }

                // task_definition.output_params 是 OUT 白名单 — Task 内部 dealOutParam 时按这份过滤
                List<Property> outputParamsSpec =
                        taskInstanceService.findTaskDefinitionOutputParams(instance.getTaskDefinitionCode());

                TaskExecutionContext ctx = TaskExecutionContext.builder()
                        .taskInstanceId(taskInstanceId)
                        .taskName(instance.getName() != null ? instance.getName() : "task-" + taskInstanceId)
                        .taskType(instance.getTaskType())
                        .paramsJson(paramsJson)
                        .executePath(executePath)
                        .resolvedFilePaths(resolvedFilePaths)
                        .executionMode(executionMode)
                        .timeoutSeconds(timeoutSeconds)
                        .prepareParams(prepareParams)
                        .outputParamsSpec(outputParamsSpec)
                        .envVars(runtimeConfigService.envVars())
                        .build();

                // 选工厂:active runtime 接管该 TaskType → 用云上子类工厂(如 AliyunSparkSqlTask);
                // 否则 → 用 channel 默认工厂(原生 SparkSqlTask 等)。两路共用 Task.handle() 入口。
                TaskFactory factory = runtimeConfigService.taskFactoryFor(ctx.getTaskType())
                        .orElse(channel::createTask);
                task = factory.create(ctx);
                runningTasks.put(taskInstanceId, task);

                // === 管道：注入资源(对云上子类同样有效,因为它们继承自原生 Task) ===
                InjectionContext injCtx = InjectionContext.builder()
                        .instance(instance)
                        .build();
                pipeline.injectResources(task, injCtx);

                task.init(ctx);
                task.handle();

                runningTasks.remove(taskInstanceId);

                // 检查执行期间是否被取消
                if (task.getStatus() == TaskStatus.CANCELLED) {
                    log.info("Task was cancelled during execution");
                    TaskInstance current = taskInstanceService.findByIdInternal(taskInstanceId);
                    if (current != null && current.getStatus() != InstanceStatus.CANCELLED) {
                        markFinished(current, InstanceStatus.CANCELLED, CANCELLED_BY_USER);
                    }
                    return;
                }

                // 收集出口元信息:Task 自己持的 sink 已经在 handle() 内把数据落盘 + 上传完成,
                // 这里只把指针(resultPath / rowCount / appId / trackingUrl)抄到 instance。
                if (task instanceof ResultableTask rt && !rt.getResultColumnMetas().isEmpty()) {
                    instance.setResultPath(rt.getResultPath());
                    instance.setRowCount(rt.getRowCount());
                }

                boolean streaming = false;
                if (task instanceof JobTask jt) {
                    if (jt.getAppId() != null) {
                        instance.setAppId(jt.getAppId());
                    }
                    if (jt.getTrackingUrl() != null) {
                        instance.setTrackingUrl(jt.getTrackingUrl());
                    }
                    streaming = jt.isStreaming();
                }

                // 流式作业:handle() 提交后返回,作业在集群侧保持 RUNNING
                if (streaming) {
                    instance.setStatus(InstanceStatus.RUNNING);
                    taskInstanceService.updateInternal(instance);
                    log.info("Streaming task submitted, appId={}, job stays RUNNING", instance.getAppId());
                    return;
                }

                // 批处理任务：成功
                instance.setStatus(InstanceStatus.SUCCESS);
                instance.setFinishedAt(LocalDateTime.now());
                instance.setDuration(Duration.between(instance.getStartedAt(), instance.getFinishedAt()).toMillis());

                // 出口 varPool:Task 自己已经按 ctx.outputParamsSpec 过滤过(对齐 DS dealOutParam)。
                // Worker 这里只做长度截断(防 LIST/JSON 巨值膨胀 varPool),不再二次过滤。
                List<Property> outputs = capOutputValueLength(task.getVarPool());

                if (!outputs.isEmpty()) {
                    instance.setVarPool(JsonUtils.toJson(outputs));
                }

                taskInstanceService.updateInternal(instance);
                log.info("Done ✓ {} rows, {}ms", instance.getRowCount(), instance.getDuration());

            } catch (Throwable e) {
                // Error 也要标 FAILED,否则 DB 行卡 RUNNING、Server 永远收不到完成回调。
                runningTasks.remove(taskInstanceId);

                boolean cancelled = task != null && task.getStatus() == TaskStatus.CANCELLED;
                if (cancelled) {
                    log.warn(CANCELLED_BY_USER);
                    markFinished(instance, InstanceStatus.CANCELLED, CANCELLED_BY_USER);
                } else {
                    String summary = ExceptionFormatter.summarize(e);
                    log.error("Task failed: {}", summary, e);
                    markFinished(instance, InstanceStatus.FAILED, summary);
                }
            } finally {
                // 清理任务工作目录
                resourceResolver.cleanup(executePath);

                // 上传日志到 FileStorage
                logService.uploadLog(storageLogPath);

                // RPC 回调通知 Server 任务完成
                sendTaskCallback(taskInstanceId, instance);
            }
        }
    }

    /** 单条 OUT 参数 value 的长度上限 (UTF-8 字节估算 = char 长度)。超过截断 + warn,防 varPool 膨胀。 */
    private static final int MAX_OUTPUT_VALUE_LENGTH = 64 * 1024;

    /** value 超过 64KB 截断 + warn。其他字段(prop/direct/type)原样保留。 */
    private List<Property> capOutputValueLength(List<Property> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<Property> capped = new ArrayList<>(outputs.size());
        for (Property p : outputs) {
            String v = p.getValue();
            if (v != null && v.length() > MAX_OUTPUT_VALUE_LENGTH) {
                log.warn("Output param '{}' value too long ({} chars), truncating to {} chars",
                        p.getProp(), v.length(), MAX_OUTPUT_VALUE_LENGTH);
                capped.add(Property.builder()
                        .prop(p.getProp())
                        .direct(p.getDirect())
                        .type(p.getType())
                        .value(v.substring(0, MAX_OUTPUT_VALUE_LENGTH))
                        .build());
            } else {
                capped.add(p);
            }
        }
        return capped;
    }

    private void sendTaskCallback(Long taskInstanceId, TaskInstance instance) {
        String callbackAddr = callbackAddresses.remove(taskInstanceId);
        if (callbackAddr == null || instance == null || !instance.getStatus().isFinished()) {
            return;
        }

        try {
            ITaskCallbackService callback = Clients.create(rpcClient, ITaskCallbackService.class, callbackAddr);
            callback.onTaskCompleted(TaskCompletionReport.builder()
                    .taskInstanceId(instance.getId())
                    .taskDefinitionCode(instance.getTaskDefinitionCode())
                    .workflowInstanceId(instance.getWorkflowInstanceId())
                    .status(instance.getStatus().name())
                    .errorMessage(instance.getErrorMessage())
                    .varPool(instance.getVarPool())
                    .rowCount(instance.getRowCount())
                    .duration(instance.getDuration())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send task completion callback to {}: {}", callbackAddr, e.getMessage());
            // 回调失败不影响任务本身，Server 有兜底轮询
        }
    }

    /**
     * 获取任务参数 JSON。content 已是完整的 TaskParams JSON，直接返回。
     */
    private String buildParamsJson(TaskInstance instance) {
        return instance.getContent() != null && !instance.getContent().isBlank()
                ? instance.getContent()
                : "{}";
    }

}
