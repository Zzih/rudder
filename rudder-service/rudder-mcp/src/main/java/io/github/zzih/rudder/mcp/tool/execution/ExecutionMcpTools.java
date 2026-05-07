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

package io.github.zzih.rudder.mcp.tool.execution;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.common.execution.ResultResponse;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.WorkspaceGuard;
import io.github.zzih.rudder.service.script.TaskInstanceService;
import io.github.zzih.rudder.service.script.dto.TaskInstanceDTO;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Execution 域 MCP tools 聚合（view_status + view_result + run + cancel）。 */
@Service
@RequiredArgsConstructor
public class ExecutionMcpTools {

    private static final int DEFAULT_RESULT_LIMIT = 100;
    private static final int MAX_RESULT_LIMIT = 1000;

    private final TaskInstanceService taskInstanceService;
    private final WorkspaceGuard workspaceGuard;

    @McpResource(uri = "rudder://execution/{id}", name = "rudder-execution", description = "A task execution instance (status, host, timestamps, etc.). Discover ids via execution.run_* or workflow_instance.list_nodes.", mimeType = "application/json")
    @McpCapability("execution.view_status")
    public String get(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        return JsonUtils.toJson(taskInstanceService.getByIdDetail(Long.parseLong(id)));
    }

    @McpTool(name = "execution.log", description = "Fetch task execution log from offsetLine (default 0).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("execution.view_status")
    public LogResponse log(
                           @McpToolParam(description = "execution id", required = true) Long executionId,
                           @McpToolParam(description = "log line offset (default 0)") Integer offsetLine) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId required");
        }
        return taskInstanceService.getLog(executionId, offsetLine == null ? 0 : offsetLine);
    }

    @McpTool(name = "execution.result", description = "Fetch task execution result rows (columns + paged rows).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("execution.view_result")
    public ResultResponse result(
                                 @McpToolParam(description = "execution id", required = true) Long executionId,
                                 @McpToolParam(description = "row offset (default 0)") Integer offset,
                                 @McpToolParam(description = "row limit (default 100, max 1000)") Integer limit) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId required");
        }
        int o = offset == null ? 0 : Math.max(0, offset);
        int l = limit == null ? DEFAULT_RESULT_LIMIT : Math.min(MAX_RESULT_LIMIT, Math.max(1, limit));
        return taskInstanceService.getResult(executionId, o, l);
    }

    @McpTool(name = "execution.run_direct", description = "Submit ad-hoc SQL/script for execution (no script record persisted). Returns immediately with an instance summary including 'id'. "
            + "This is asynchronous — to get the actual result, poll the resource `rudder://execution/{id}` until status reaches SUCCESS / FAILED / CANCELLED, "
            + "then call `execution.result` for output rows or `execution.log` for stdout/stderr.", annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = true))
    @McpCapability("execution.run")
    public TaskInstanceDTO runDirect(
                                     @McpToolParam(description = "task type", required = true) TaskType taskType,
                                     @McpToolParam(description = "datasource id (null for non-DB tasks)") Long datasourceId,
                                     @McpToolParam(description = "SQL or script body", required = true) String sql,
                                     @McpToolParam(description = "execution mode (e.g. STREAM / BATCH)") String executionMode) {
        if (taskType == null) {
            throw new IllegalArgumentException("taskType required");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql required");
        }
        return taskInstanceService.executeDirectDetail(
                UserContext.requireWorkspaceId(), taskType, datasourceId, sql, executionMode);
    }

    @McpTool(name = "execution.run_script", description = "Submit an existing script for execution, optionally overriding SQL / datasource / params. Returns immediately with an instance summary including 'id'. "
            + "Asynchronous — poll `rudder://execution/{id}` until terminal status, then `execution.result` or `execution.log`. Use `execution.cancel` to abort.", annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = true))
    @McpCapability("execution.run")
    public TaskInstanceDTO runScript(
                                     @McpToolParam(description = "script code", required = true) Long scriptCode,
                                     @McpToolParam(description = "datasource id override (null = use bound)") Long datasourceId,
                                     @McpToolParam(description = "override SQL (null = use script content)") String overrideSql,
                                     @McpToolParam(description = "execution mode") String executionMode,
                                     @McpToolParam(description = "runtime params") Map<String, String> params) {
        if (scriptCode == null) {
            throw new IllegalArgumentException("scriptCode required");
        }
        workspaceGuard.requireScriptInWorkspace(scriptCode);
        return taskInstanceService.executeDetail(scriptCode, datasourceId, overrideSql, executionMode, params);
    }

    @McpTool(name = "execution.cancel", description = "Cancel a running task instance.", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("execution.cancel")
    public void cancel(
                       @McpToolParam(description = "execution id", required = true) Long executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId required");
        }
        taskInstanceService.cancel(executionId);
    }
}
