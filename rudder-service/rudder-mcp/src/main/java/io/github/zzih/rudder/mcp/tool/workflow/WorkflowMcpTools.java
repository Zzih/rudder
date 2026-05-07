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

package io.github.zzih.rudder.mcp.tool.workflow;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.enums.TriggerType;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.WorkspaceGuard;
import io.github.zzih.rudder.service.workflow.WorkflowDefinitionService;
import io.github.zzih.rudder.service.workflow.WorkflowInstanceService;
import io.github.zzih.rudder.service.workflow.WorkflowPublishService;
import io.github.zzih.rudder.service.workflow.dto.PublishRecordDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowInstanceDTO;
import io.github.zzih.rudder.service.workflow.executor.WorkflowExecutor;
import io.github.zzih.rudder.service.workflow.executor.varpool.VarPoolManager;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Workflow 域 MCP tools 聚合（browse + author + run + publish）。 */
@Service
@RequiredArgsConstructor
public class WorkflowMcpTools {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowPublishService workflowPublishService;
    private final WorkflowExecutor workflowExecutor;
    private final WorkspaceGuard workspaceGuard;

    /** 工作流列表项 — 故意只投影 4 个轻量字段（drop DAG JSON / globalParams 等大字段）。 */
    public record WorkflowSummary(Long code, String name, Long projectCode, String description) {
    }

    @McpTool(name = "workflow.list", description = "List workflow definitions in the current workspace (metadata only, no DAG).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("workflow.browse")
    public List<WorkflowSummary> list() {
        return workflowDefinitionService.listByWorkspaceId(UserContext.requireWorkspaceId()).stream()
                .map(wf -> new WorkflowSummary(
                        wf.getCode(), wf.getName(), wf.getProjectCode(), wf.getDescription()))
                .toList();
    }

    @McpResource(uri = "rudder://workflow/{projectCode}/{code}", name = "rudder-workflow", description = "A workflow definition (DAG of tasks). Use workflow.list to discover (projectCode, code) pairs.", mimeType = "application/json")
    @McpCapability("workflow.browse")
    public String get(String projectCode, String code) {
        if (projectCode == null || projectCode.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("projectCode and code required");
        }
        return JsonUtils.toJson(workflowDefinitionService.getByCodeDetail(
                UserContext.requireWorkspaceId(), Long.parseLong(projectCode), Long.parseLong(code)));
    }

    @McpTool(name = "workflow.create", description = "Create a workflow definition. body.dagJson must follow the platform DAG schema.")
    @McpCapability("workflow.author")
    public WorkflowDefinitionDTO create(
                                        @McpToolParam(description = "project code (workflow lives under this project)", required = true) Long projectCode,
                                        @McpToolParam(description = "Workflow body — name required; dagJson defines the DAG topology", required = true) WorkflowDefinitionDTO body) {
        if (projectCode == null || body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("projectCode and name required");
        }
        workspaceGuard.requireProjectInWorkspace(projectCode);
        return workflowDefinitionService.createDetail(UserContext.requireWorkspaceId(), projectCode, body);
    }

    @McpTool(name = "workflow.delete", description = "Delete a workflow definition by code.", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("workflow.author")
    public void delete(
                       @McpToolParam(description = "project code", required = true) Long projectCode,
                       @McpToolParam(description = "workflow code", required = true) Long code) {
        if (projectCode == null || code == null) {
            throw new IllegalArgumentException("projectCode and code required");
        }
        workflowDefinitionService.delete(UserContext.requireWorkspaceId(), projectCode, code);
    }

    @McpTool(name = "workflow.run", description = "Trigger a workflow run (manual trigger) with optional runtime params. Returns immediately with the new instance summary including 'id'. "
            + "Asynchronous — poll `rudder://workflow_instance/{workflowCode}/{id}` for instance-level status; per-node details via "
            + "`rudder://workflow_instance/{id}/nodes`. Use `workflow_instance.cancel` to abort a running workflow.", annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = true))
    @McpCapability("workflow.run")
    public WorkflowInstanceDTO run(
                                   @McpToolParam(description = "project code", required = true) Long projectCode,
                                   @McpToolParam(description = "workflow code", required = true) Long code,
                                   @McpToolParam(description = "runtime parameter overrides") Map<String, String> overrideParams) {
        if (projectCode == null || code == null) {
            throw new IllegalArgumentException("projectCode and code required");
        }
        Long workspaceId = UserContext.requireWorkspaceId();
        // 校验工作流归属当前 workspace（getByCodeDetail 内含 workspace 校验）
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);

        var props = VarPoolManager.wrapAsProperties(overrideParams, Direct.IN);
        WorkflowInstanceDTO instance =
                workflowInstanceService.createInstanceDTO(code, TriggerType.MANUAL, props);
        workflowExecutor.execute(instance.getId());
        return instance;
    }

    @McpTool(name = "workflow.publish", description = "Submit workflow publish (creates a version + approval record).", annotations = @McpTool.McpAnnotations(destructiveHint = true))
    @McpCapability("workflow.publish")
    public PublishRecordDTO publish(
                                    @McpToolParam(description = "project code", required = true) Long projectCode,
                                    @McpToolParam(description = "workflow code", required = true) Long workflowCode,
                                    @McpToolParam(description = "existing version id to re-publish (null = new)") Long existingVersionId,
                                    @McpToolParam(description = "remark") String remark) {
        if (projectCode == null || workflowCode == null) {
            throw new IllegalArgumentException("projectCode and workflowCode required");
        }
        workspaceGuard.requireWorkflowInWorkspace(projectCode, workflowCode);
        return workflowPublishService.submitPublish(workflowCode, existingVersionId, projectCode, remark);
    }
}
