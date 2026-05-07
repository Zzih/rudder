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
import io.github.zzih.rudder.common.page.PageRequest;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.Page;
import io.github.zzih.rudder.service.workflow.WorkflowInstanceService;
import io.github.zzih.rudder.service.workflow.dto.WorkflowInstanceDTO;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/** WorkflowInstance 域 MCP tools — 工作流运行实例查询、节点查看、取消。 */
@Service
@RequiredArgsConstructor
public class WorkflowInstanceMcpTools {

    private final WorkflowInstanceService workflowInstanceService;

    @McpTool(name = "workflow_instance.list", description = "List workflow run instances in the current workspace (most recent first).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("workflow.browse")
    public List<WorkflowInstanceDTO> list() {
        return workflowInstanceService.listByWorkspaceIdDTO(UserContext.requireWorkspaceId());
    }

    @McpTool(name = "workflow_instance.page_by_workflow", description = "Page through run instances of a specific workflow definition.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("workflow.browse")
    public Page<WorkflowInstanceDTO> pageByWorkflow(
                                                    @McpToolParam(description = "workflow definition code", required = true) Long workflowDefinitionCode,
                                                    @McpToolParam(description = "search keyword (matches instance name)") String searchVal,
                                                    @McpToolParam(description = "page number (1-based)") Integer pageNum,
                                                    @McpToolParam(description = "page size (default 20, max 100)") Integer pageSize) {
        if (workflowDefinitionCode == null) {
            throw new IllegalArgumentException("workflowDefinitionCode required");
        }
        int p = PageRequest.normalizePageNum(pageNum == null ? 1 : pageNum);
        int s = PageRequest.normalizePageSize(pageSize == null ? 20 : pageSize);
        IPage<WorkflowInstanceDTO> page =
                workflowInstanceService.pageByWorkflowDefinitionCodeDTO(workflowDefinitionCode, searchVal, p, s);
        return Page.of(page.getTotal(), p, s, page.getRecords());
    }

    @McpResource(uri = "rudder://workflow_instance/{workflowCode}/{instanceId}", name = "rudder-workflow-instance", description = "A workflow run instance (status, version, runtime params, dag snapshot). Discover instance ids via workflow_instance.list / page_by_workflow.", mimeType = "application/json")
    @McpCapability("workflow.browse")
    public String get(String workflowCode, String instanceId) {
        if (workflowCode == null || workflowCode.isBlank() || instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("workflowCode and instanceId required");
        }
        return JsonUtils.toJson(workflowInstanceService.getByIdDTO(
                UserContext.requireWorkspaceId(), Long.parseLong(workflowCode), Long.parseLong(instanceId)));
    }

    @McpResource(uri = "rudder://workflow_instance/{instanceId}/nodes", name = "rudder-workflow-instance-nodes", description = "Per-node task-instance details of a workflow run (status, log path, execution host).", mimeType = "application/json")
    @McpCapability("workflow.browse")
    public String listNodes(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId required");
        }
        return JsonUtils.toJson(workflowInstanceService.listNodeInstancesDTO(Long.parseLong(instanceId)));
    }

    @McpTool(name = "workflow_instance.cancel", description = "Cancel a running workflow instance (sends cancel to all running node task-instances).", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("workflow.run")
    public void cancel(
                       @McpToolParam(description = "workflow instance id", required = true) Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId required");
        }
        workflowInstanceService.cancel(instanceId);
    }
}
