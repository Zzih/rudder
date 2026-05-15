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

package io.github.zzih.rudder.service.script;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.common.execution.ResultResponse;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.io.StoragePathUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.InstanceNameUtils;
import io.github.zzih.rudder.dao.dao.*;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.dao.enums.TriggerType;
import io.github.zzih.rudder.service.download.DownloadFormat;
import io.github.zzih.rudder.service.download.ResultDownloadWriter;
import io.github.zzih.rudder.service.registry.NodeRouter;
import io.github.zzih.rudder.service.script.dto.TaskInstanceDTO;
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskCategory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskInstanceService {

    private final TaskInstanceDao taskInstanceDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ScriptDao scriptDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;
    private final WorkspaceDao workspaceDao;
    private final ProjectDao projectDao;
    private final TaskDispatchService taskDispatchService;
    private final NodeRouter nodeRouter;

    public TaskInstance executeDirect(TaskType taskType, Long datasourceId, String sql, String executionMode) {
        return executeDirect(null, taskType, datasourceId, sql, executionMode, null);
    }

    public TaskInstance executeDirect(Long workspaceId, TaskType taskType, Long datasourceId, String sql,
                                      String executionMode, Map<String, String> params) {
        TaskInstance instance = new TaskInstance();
        instance.setName(generateInstanceName(taskType.name()));
        instance.setTaskType(taskType);
        instance.setSourceType(SourceType.IDE);
        instance.setWorkspaceId(workspaceId);
        // 构造 TaskParams JSON 作为 content
        instance.setContent(JsonUtils.toJson(Map.of(
                "sql", sql != null ? sql : "",
                "dataSourceId", datasourceId != null ? datasourceId : 0,
                "executionMode", executionMode != null ? executionMode : ExecutionMode.BATCH.name())));
        instance.setStatus(InstanceStatus.PENDING);
        if (params != null && !params.isEmpty()) {
            instance.setParams(serializeParams(params));
        }
        taskInstanceDao.insert(instance);

        // 生成 logPath（insert 后才有 id）
        instance.setLogPath(
                StoragePathUtils.logPath("default", null, null, null, instance.getName(), instance.getId()));
        taskInstanceDao.updateById(instance);

        String executionHost = taskDispatchService.dispatch(instance.getId());
        instance.setExecutionHost(executionHost);
        taskInstanceDao.updateById(instance);

        log.info("Direct execution dispatched: id={}, taskType={}, node={}", instance.getId(), taskType, executionHost);
        return instance;
    }

    public TaskInstance execute(Long scriptCode, Long datasourceId, String overrideSql, String executionMode) {
        return execute(scriptCode, datasourceId, overrideSql, executionMode, null);
    }

    public TaskInstance execute(Long scriptCode, Long datasourceId, String overrideSql, String executionMode,
                                Map<String, String> params) {
        Script script = scriptDao.selectByCode(scriptCode);
        if (script == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_NOT_FOUND);
        }

        // content 直接使用 Script 的 TaskParams JSON
        // 如果前端传了 overrideSql 或 datasourceId，覆盖到 JSON 中
        String content = script.getContent();
        if ((overrideSql != null && !overrideSql.isBlank()) || datasourceId != null || executionMode != null) {
            try {
                Map<String, Object> parsed = JsonUtils.fromJson(content,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
                if (overrideSql != null && !overrideSql.isBlank()) {
                    // SQL 类型覆盖 sql 字段，SCRIPT 类型覆盖 content 字段
                    TaskCategory category = script.getTaskType() != null ? script.getTaskType().getCategory() : null;
                    if (category == TaskCategory.SQL) {
                        parsed.put("sql", overrideSql.trim());
                    } else {
                        parsed.put("content", overrideSql.trim());
                    }
                }
                if (datasourceId != null) {
                    parsed.put("dataSourceId", datasourceId);
                }
                if (executionMode != null) {
                    parsed.put("executionMode", executionMode);
                }
                content = JsonUtils.toJson(parsed);
            } catch (Exception e) {
                // 兼容：解析失败则原样使用
            }
        }

        TaskInstance instance = new TaskInstance();
        instance.setName(generateInstanceName(script.getName()));
        instance.setTaskType(script.getTaskType());
        instance.setSourceType(SourceType.IDE);
        instance.setWorkspaceId(script.getWorkspaceId());
        instance.setScriptCode(script.getCode());
        instance.setContent(content);
        instance.setStatus(InstanceStatus.PENDING);
        if (params != null && !params.isEmpty()) {
            instance.setParams(serializeParams(params));
        }
        taskInstanceDao.insert(instance);

        // 生成 logPath（insert 后才有 id）
        String wsName = resolveWorkspaceName(script.getWorkspaceId());
        instance.setLogPath(
                StoragePathUtils.logPath(wsName, script.getName(), null, null, instance.getName(), instance.getId()));
        taskInstanceDao.updateById(instance);

        String executionHost = taskDispatchService.dispatch(instance.getId());
        instance.setExecutionHost(executionHost);
        taskInstanceDao.updateById(instance);

        log.info("IDE execution dispatched: id={}, scriptCode={}, node={}", instance.getId(), scriptCode,
                executionHost);
        return instance;
    }

    /**
     * 在工作流上下文中执行单个任务。
     * 创建一个 WorkflowInstance（仅包含单节点），让 DEPENDENT 等节点能通过 workflowDefinitionCode 查到。
     * 任务完成后自动更新 WorkflowInstance 状态。
     */
    public TaskInstance executeInWorkflowDefinition(Long workflowDefinitionCode, Long taskDefinitionCode,
                                                    TaskType taskType, Long datasourceId,
                                                    String sql, String executionMode) {
        WorkflowDefinition workflow = workflowDefinitionDao.selectByCode(workflowDefinitionCode);
        if (workflow == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_NOT_FOUND,
                    "WorkflowDefinition not found: " + workflowDefinitionCode);
        }

        // 创建 WorkflowInstance
        LocalDateTime now = LocalDateTime.now();

        WorkflowInstance wfInstance = new WorkflowInstance();
        wfInstance.setWorkspaceId(workflow.getWorkspaceId());
        wfInstance.setProjectCode(workflow.getProjectCode());
        wfInstance.setWorkflowDefinitionCode(workflow.getCode());
        wfInstance.setName(InstanceNameUtils.snapshotName(workflow.getName() + "_debug", now));
        wfInstance.setTriggerType(TriggerType.MANUAL);
        wfInstance.setStatus(InstanceStatus.RUNNING);
        wfInstance.setDagSnapshot(workflow.getDagJson());
        wfInstance.setStartedAt(now);
        workflowInstanceDao.insert(wfInstance);

        // 创建 TaskInstance 并关联到 WorkflowInstance
        TaskInstance instance = new TaskInstance();
        instance.setName(generateInstanceName(taskType.name()));
        instance.setTaskType(taskType);
        instance.setSourceType(SourceType.IDE);
        instance.setWorkspaceId(workflow.getWorkspaceId());
        instance.setWorkflowInstanceId(wfInstance.getId());
        instance.setTaskDefinitionCode(taskDefinitionCode);
        instance.setContent(JsonUtils.toJson(Map.of(
                "sql", sql != null ? sql : "",
                "dataSourceId", datasourceId != null ? datasourceId : 0,
                "executionMode", executionMode != null ? executionMode : ExecutionMode.BATCH.name())));
        instance.setStatus(InstanceStatus.PENDING);
        taskInstanceDao.insert(instance);

        // 生成 logPath
        String wsName = resolveWorkspaceName(workflow.getWorkspaceId());
        instance.setLogPath(StoragePathUtils.logPath(wsName, null, workflow.getCode(), wfInstance.getId(),
                instance.getName(), instance.getId()));
        taskInstanceDao.updateById(instance);

        // 分发执行
        String executionHost = taskDispatchService.dispatch(instance.getId());
        instance.setExecutionHost(executionHost);
        taskInstanceDao.updateById(instance);

        log.info(
                "WorkflowDefinition debug execution dispatched: taskInstanceId={}, workflowInstanceId={}, workflowDefinitionCode={}, node={}",
                instance.getId(), wfInstance.getId(), workflowDefinitionCode, executionHost);
        return instance;
    }

    private void assertWorkspaceAccess(TaskInstance instance) {
        UserContext.UserInfo user = UserContext.get();
        if (user == null) {
            return;
        }
        if (RoleType.SUPER_ADMIN.name().equals(user.getRole())) {
            return;
        }
        Long instanceWorkspaceId = instance.getWorkspaceId();
        if (instanceWorkspaceId == null) {
            return;
        }
        Long userWorkspaceId = user.getWorkspaceId();
        if (userWorkspaceId == null || !userWorkspaceId.equals(instanceWorkspaceId)) {
            log.warn("Cross-workspace access denied: user={}, userWorkspace={}, instance={}, instanceWorkspace={}",
                    user.getUserId(), userWorkspaceId, instance.getId(), instanceWorkspaceId);
            throw new BizException(ScriptErrorCode.TASK_INSTANCE_NO_ACCESS, instance.getId());
        }
    }

    private String resolveWorkspaceName(Long workspaceId) {
        if (workspaceId == null) {
            return "default";
        }
        var ws = workspaceDao.selectById(workspaceId);
        return ws != null && ws.getName() != null ? ws.getName() : "default";
    }

    private String generateInstanceName(String baseName) {
        return InstanceNameUtils.snapshotName(baseName);
    }

    // TaskWorker 把 instance.params 当 List<Property> 反序列化（与 WorkflowInstanceRunner 写入格式对齐），
    // IDE 直跑入口拿到的是 Map<String,String>，必须转一下再落库,否则 Worker 端 JsonUtils.toList 失败。
    private String serializeParams(Map<String, String> params) {
        List<Property> properties = new ArrayList<>(params.size());
        params.forEach((k, v) -> properties.add(Property.builder()
                .prop(k)
                .direct(Direct.IN)
                .value(v)
                .build()));
        return JsonUtils.toJson(properties);
    }

    public TaskInstance getById(Long id) {
        TaskInstance instance = taskInstanceDao.selectById(id);
        if (instance == null) {
            throw new NotFoundException(ScriptErrorCode.EXECUTION_NOT_FOUND);
        }
        // 工作空间归属校验：防止跨工作空间越权（IDOR）
        // 仅在存在 UserContext（HTTP 调用链）时强制；RPC 回调和调度内部调用无 UserContext，直接放行
        assertWorkspaceAccess(instance);
        // debug 执行的任务完成后，同步更新关联的 WorkflowInstance 状态
        if (instance.getStatus().isFinished() && instance.getWorkflowInstanceId() != null
                && instance.getSourceType() == SourceType.IDE) {
            WorkflowInstance wfInstance = workflowInstanceDao.selectById(instance.getWorkflowInstanceId());
            if (wfInstance != null && !wfInstance.getStatus().isFinished()) {
                wfInstance.setStatus(instance.getStatus() == InstanceStatus.SUCCESS
                        ? InstanceStatus.SUCCESS
                        : InstanceStatus.FAILED);
                wfInstance.setFinishedAt(instance.getFinishedAt());
                workflowInstanceDao.updateById(wfInstance);
            }
        }
        return instance;
    }

    public List<TaskInstance> listByScriptCode(Long scriptCode) {
        return taskInstanceDao.selectByScriptCodeOrderByCreatedAtDesc(scriptCode);
    }

    public List<TaskInstance> listRunning() {
        return taskInstanceDao.selectRunningAndPending();
    }

    public IPage<TaskInstance> pageRunning(
                                           Long workspaceId, String name,
                                           String taskType, String runtimeType,
                                           int pageNum, int pageSize) {
        return taskInstanceDao.selectRunningPage(workspaceId, name, taskType, runtimeType, pageNum, pageSize);
    }

    public List<TaskInstance> listRunningByScriptCode(Long scriptCode) {
        return taskInstanceDao.selectRunningByScriptCode(scriptCode);
    }

    /** controller 用,DTO 版分页运行中任务。 */
    public IPage<TaskInstanceDTO> pageRunningDetail(
                                                    Long workspaceId,
                                                    String name,
                                                    String taskType,
                                                    String runtimeType,
                                                    int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                pageRunning(workspaceId, name, taskType, runtimeType, pageNum, pageSize),
                TaskInstanceDTO.class);
    }

    /** controller 用,DTO 版按 scriptCode 拉运行中任务。 */
    public List<TaskInstanceDTO> listRunningByScriptCodeDetail(Long scriptCode) {
        return BeanConvertUtils.convertList(listRunningByScriptCode(scriptCode), TaskInstanceDTO.class);
    }

    public void cancel(Long executionId) {
        log.info("取消任务执行, executionId={}", executionId);
        TaskInstance instance = getById(executionId);
        taskDispatchService.cancelOnExecution(instance.getExecutionHost(), executionId);
    }

    public String triggerSavepoint(Long executionId) {
        log.info("触发Savepoint, executionId={}", executionId);
        TaskInstance instance = getById(executionId);
        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw new IllegalStateException("Task is not running, current status: " + instance.getStatus());
        }
        if (instance.getAppId() == null || instance.getAppId().isBlank()) {
            throw new IllegalStateException("No app ID for savepoint");
        }
        return taskDispatchService.triggerSavepoint(instance.getExecutionHost(), executionId);
    }

    public LogResponse getLog(Long id, int offsetLine) {
        TaskInstance instance = getById(id);
        String host = nodeRouter.pickFetchHost(id, instance.getExecutionHost());
        return taskDispatchService.fetchLog(host, instance.getLogPath(), offsetLine);
    }

    // ==================== DTO-returning methods for Controller ====================

    public TaskInstanceDTO executeDirectDetail(Long workspaceId, TaskType taskType, Long datasourceId, String sql,
                                               String executionMode) {
        return BeanConvertUtils.convert(executeDirect(workspaceId, taskType, datasourceId, sql, executionMode, null),
                TaskInstanceDTO.class);
    }

    public TaskInstanceDTO executeDetail(Long scriptCode, Long datasourceId, String overrideSql, String executionMode,
                                         Map<String, String> params) {
        return BeanConvertUtils.convert(execute(scriptCode, datasourceId, overrideSql, executionMode, params),
                TaskInstanceDTO.class);
    }

    public TaskInstanceDTO executeInWorkflowDefinitionDetail(Long workflowDefinitionCode, Long taskDefinitionCode,
                                                             TaskType taskType, Long datasourceId,
                                                             String sql, String executionMode) {
        return BeanConvertUtils.convert(executeInWorkflowDefinition(workflowDefinitionCode, taskDefinitionCode,
                taskType, datasourceId, sql, executionMode), TaskInstanceDTO.class);
    }

    public TaskInstanceDTO getByIdDetail(Long id) {
        return BeanConvertUtils.convert(getById(id), TaskInstanceDTO.class);
    }

    public List<TaskInstanceDTO> listByScriptCodeDetail(Long scriptCode) {
        return BeanConvertUtils.convertList(listByScriptCode(scriptCode), TaskInstanceDTO.class);
    }

    public ResultResponse getResult(Long id, int offset, int limit) {
        TaskInstance instance = getById(id);
        String host = nodeRouter.pickFetchHost(id, instance.getExecutionHost());
        return taskDispatchService.fetchResult(host, instance.getResultPath(), offset, limit);
    }

    /** 1000 行 ≈ 1MB 量级,内存峰值 = 1 batch + writer buffer。 */
    private static final int DOWNLOAD_BATCH = 1000;

    /** 一次 getById + 校验,把 controller 设 header 和 service 拉数据要的所有信息一并产出。 */
    public record DownloadHandle(String filename, String executionHost, String resultPath) {
    }

    public DownloadHandle prepareDownload(Long id) {
        TaskInstance instance = getById(id);
        if (instance.getResultPath() == null || instance.getResultPath().isBlank()) {
            throw new NotFoundException(ScriptErrorCode.EXECUTION_NOT_FOUND);
        }
        String name = instance.getName() != null && !instance.getName().isBlank()
                ? instance.getName() + "-" + id
                : "result-" + id;
        String host = nodeRouter.pickFetchHost(id, instance.getExecutionHost());
        return new DownloadHandle(name, host, instance.getResultPath());
    }

    public void streamDownloadBody(DownloadHandle handle, DownloadFormat format, OutputStream out) throws IOException {
        try (ResultDownloadWriter writer = format.newWriter(out)) {
            int offset = 0;
            boolean headerWritten = false;
            while (true) {
                ResultResponse page = taskDispatchService.fetchResult(
                        handle.executionHost(), handle.resultPath(), offset, DOWNLOAD_BATCH);
                List<Map<String, Object>> rows = page.getRows();
                if (!headerWritten) {
                    writer.writeHeader(page.getColumns());
                    headerWritten = true;
                }
                for (Map<String, Object> row : rows) {
                    writer.writeRow(row);
                }
                if (rows.size() < DOWNLOAD_BATCH) {
                    break;
                }
                offset += DOWNLOAD_BATCH;
            }
        }
    }

}
