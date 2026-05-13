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

import io.github.zzih.rudder.api.response.TaskInstanceResponse;
import io.github.zzih.rudder.api.response.WorkflowInstanceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.script.dto.TaskInstanceDTO;
import io.github.zzih.rudder.service.workflow.WorkflowInstanceService;
import io.github.zzih.rudder.service.workflow.dto.WorkflowInstanceDTO;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/workflow-instances")
@RequiredArgsConstructor
@RequireViewer
public class WorkflowInstanceController {

    private final WorkflowInstanceService workflowInstanceService;

    @GetMapping
    public PageResult<WorkflowInstanceResponse> list(
                                                     @PathVariable Long workspaceId,
                                                     @RequestParam(required = false) Long workflowDefinitionCode,
                                                     @RequestParam(required = false) Long projectCode,
                                                     @RequestParam(required = false) String searchVal,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        if (workflowDefinitionCode != null) {
            IPage<WorkflowInstanceDTO> page = workflowInstanceService
                    .pageByWorkflowDefinitionCodeDTO(workflowDefinitionCode, searchVal, pageNum, pageSize);
            return PageResult.of(BeanConvertUtils.convertList(page.getRecords(), WorkflowInstanceResponse.class),
                    page.getTotal(), pageNum, pageSize);
        }
        if (projectCode != null) {
            IPage<WorkflowInstanceDTO> page =
                    workflowInstanceService.pageByProjectCodeDTO(projectCode, searchVal, status, pageNum, pageSize);
            return PageResult.of(BeanConvertUtils.convertList(page.getRecords(), WorkflowInstanceResponse.class),
                    page.getTotal(), pageNum, pageSize);
        }
        // fallback
        List<WorkflowInstanceDTO> list = workflowInstanceService.listByWorkspaceIdDTO(workspaceId);
        List<WorkflowInstanceResponse> responses = BeanConvertUtils.convertList(list, WorkflowInstanceResponse.class);
        return PageResult.of(responses, responses.size(), 1, responses.size());
    }

    @GetMapping("/{id}")
    public Result<WorkflowInstanceResponse> getById(@PathVariable Long workspaceId,
                                                    @RequestParam Long workflowDefinitionCode,
                                                    @PathVariable Long id) {
        return Result.ok(
                BeanConvertUtils.convert(workflowInstanceService.getByIdDTO(workspaceId, workflowDefinitionCode, id),
                        WorkflowInstanceResponse.class));
    }

    /**
     * 查询工作流实例下的任务实例（节点执行记录）。
     */
    @GetMapping("/{id}/nodes")
    public Result<List<TaskInstanceResponse>> listNodes(@PathVariable Long workspaceId,
                                                        @RequestParam Long workflowDefinitionCode,
                                                        @PathVariable Long id) {
        workflowInstanceService.getByIdDTO(workspaceId, workflowDefinitionCode, id);
        List<TaskInstanceDTO> dtos = workflowInstanceService.listNodeInstancesDTO(id);
        return Result.ok(BeanConvertUtils.convertList(dtos, TaskInstanceResponse.class));
    }

    @PostMapping("/{id}/cancel")
    @RequireDeveloper
    @AuditLog(module = AuditModule.WORKFLOW_INSTANCE, action = AuditAction.CANCEL, resourceType = AuditResourceType.WORKFLOW_INSTANCE, resourceCode = "#id")
    public Result<Void> cancel(@PathVariable Long workspaceId,
                               @RequestParam Long workflowDefinitionCode,
                               @PathVariable Long id) {
        workflowInstanceService.getByIdDTO(workspaceId, workflowDefinitionCode, id);
        workflowInstanceService.cancel(id);
        return Result.ok();
    }
}
