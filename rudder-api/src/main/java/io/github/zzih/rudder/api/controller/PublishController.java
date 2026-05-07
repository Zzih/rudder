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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.request.ProjectPublishRequest;
import io.github.zzih.rudder.api.request.WorkflowPublishRequest;
import io.github.zzih.rudder.api.response.PublishRecordResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.workflow.WorkflowDefinitionService;
import io.github.zzih.rudder.service.workflow.WorkflowPublishService;
import io.github.zzih.rudder.service.workflow.dto.PublishBatchDTO;
import io.github.zzih.rudder.service.workspace.ProjectService;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/publish")
@RequiredArgsConstructor
@RequireRole(RoleType.VIEWER)
public class PublishController {

    private final WorkflowPublishService workflowPublishService;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final ProjectService projectService;

    @PostMapping("/workflow/{workflowCode}")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.PUBLISH, action = AuditAction.PUBLISH_WORKFLOW, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#workflowCode")
    public Result<PublishRecordResponse> publishWorkflow(@PathVariable Long workspaceId,
                                                         @PathVariable Long projectCode,
                                                         @PathVariable Long workflowCode,
                                                         @Valid @RequestBody WorkflowPublishRequest request) {
        workflowDefinitionService.getByCode(workspaceId, projectCode, workflowCode);
        return Result.ok(BeanConvertUtils.convert(
                workflowPublishService.submitPublish(workflowCode, request.getVersionId(),
                        projectCode, request.getRemark()),
                PublishRecordResponse.class));
    }

    @GetMapping("/records")
    public PageResult<PublishBatchDTO> listRecords(@PathVariable Long workspaceId,
                                                   @PathVariable Long projectCode,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return workflowPublishService.pageBatches(projectCode, status, pageNum, pageSize);
    }

    @PostMapping("/project")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.PUBLISH, action = AuditAction.PUBLISH_PROJECT, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#projectCode")
    public Result<List<PublishRecordResponse>> publishProject(@PathVariable Long workspaceId,
                                                              @PathVariable Long projectCode,
                                                              @Valid @RequestBody ProjectPublishRequest request) {
        projectService.getByCode(workspaceId, projectCode);
        List<WorkflowPublishService.PublishItem> items = request.getItems().stream()
                .map(i -> new WorkflowPublishService.PublishItem(i.getWorkflowDefinitionCode(), i.getVersionId()))
                .toList();
        return Result.ok(BeanConvertUtils.convertList(
                workflowPublishService.submitProjectPublish(projectCode, items, request.getRemark()),
                PublishRecordResponse.class));
    }

    @PostMapping("/records/{batchCode}/execute")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.PUBLISH, action = AuditAction.EXECUTE_PUBLISH, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#batchCode")
    public Result<Void> executePublish(@PathVariable Long workspaceId,
                                       @PathVariable Long projectCode,
                                       @PathVariable Long batchCode) {
        workflowPublishService.executePublish(batchCode);
        return Result.ok();
    }
}
