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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.enums.ScheduleStatus;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.service.workflow.WorkflowScheduleService;
import io.github.zzih.rudder.service.workflow.dto.WorkflowScheduleDTO;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** WorkflowSchedule 域 MCP tools — 工作流定时调度配置（cron）。 */
@Service
@RequiredArgsConstructor
public class WorkflowScheduleMcpTools {

    private final WorkflowScheduleService workflowScheduleService;

    @McpResource(uri = "rudder://workflow_schedule/{workflowCode}", name = "rudder-workflow-schedule", description = "Cron schedule for a workflow definition (null contents if not scheduled). Use workflow_schedule.set to create/update. Discover workflow codes via workflow.list.", mimeType = "application/json")
    @McpCapability("workflow.browse")
    public String get(String workflowCode) {
        if (workflowCode == null || workflowCode.isBlank()) {
            throw new IllegalArgumentException("workflowCode required");
        }
        return JsonUtils
                .toJson(workflowScheduleService.getByWorkflowDefinitionCodeDetail(Long.parseLong(workflowCode)));
    }

    @McpTool(name = "workflow_schedule.set", description = "Create or update the cron schedule of a workflow definition. body.status=ONLINE enables it; OFFLINE disables.", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("workflow.author")
    public WorkflowScheduleDTO set(
                                   @McpToolParam(description = "workflow definition code", required = true) Long workflowDefinitionCode,
                                   @McpToolParam(description = "Schedule body — cronExpression required; status defaults ONLINE; timezone defaults UTC", required = true) WorkflowScheduleDTO body) {
        if (workflowDefinitionCode == null) {
            throw new IllegalArgumentException("workflowDefinitionCode required");
        }
        if (body == null || body.getCronExpression() == null || body.getCronExpression().isBlank()) {
            throw new IllegalArgumentException("cronExpression required");
        }
        if (body.getTimezone() == null || body.getTimezone().isBlank()) {
            body.setTimezone("UTC");
        }
        if (body.getStatus() == null) {
            body.setStatus(ScheduleStatus.ONLINE);
        }
        return workflowScheduleService.saveOrUpdateDetail(workflowDefinitionCode, body);
    }
}
