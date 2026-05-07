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

import io.github.zzih.rudder.api.request.ProjectCreateRequest;
import io.github.zzih.rudder.api.request.ProjectOwnerUpdateRequest;
import io.github.zzih.rudder.api.response.ProjectResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.workspace.ProjectService;
import io.github.zzih.rudder.service.workspace.dto.ProjectDTO;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
@RequireRole(RoleType.VIEWER)
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.CREATE, resourceType = AuditResourceType.PROJECT)
    public Result<ProjectResponse> create(@PathVariable Long workspaceId,
                                          @Valid @RequestBody ProjectCreateRequest request) {
        ProjectDTO body = BeanConvertUtils.convert(request, ProjectDTO.class);
        body.setParams(request.getParams() == null ? null : JsonUtils.toJson(request.getParams()));
        return Result.ok(BeanConvertUtils.convert(projectService.create(workspaceId, body), ProjectResponse.class));
    }

    @GetMapping
    public PageResult<ProjectResponse> list(@PathVariable Long workspaceId,
                                            @RequestParam(required = false) String searchVal,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<ProjectDTO> page = projectService.pageByWorkspaceId(workspaceId, searchVal, pageNum, pageSize);
        return PageResult.of(BeanConvertUtils.convertList(page.getRecords(), ProjectResponse.class), page.getTotal(),
                pageNum, pageSize);
    }

    @GetMapping("/{code}")
    public Result<ProjectResponse> getByCode(@PathVariable Long workspaceId,
                                             @PathVariable Long code) {
        return Result.ok(BeanConvertUtils.convert(projectService.getByCode(workspaceId, code), ProjectResponse.class));
    }

    /**
     * 更新项目信息。需要 DEVELOPER 角色，且必须是项目创建者或 WORKSPACE_OWNER+。
     */
    @PutMapping("/{code}")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.UPDATE, resourceType = AuditResourceType.PROJECT, resourceCode = "#projectCode")
    public Result<ProjectResponse> update(@PathVariable Long workspaceId,
                                          @PathVariable Long code,
                                          @Valid @RequestBody ProjectCreateRequest request) {
        checkProjectOwnerOrAdmin(workspaceId, code);
        ProjectDTO body = BeanConvertUtils.convert(request, ProjectDTO.class);
        body.setParams(request.getParams() == null ? null : JsonUtils.toJson(request.getParams()));
        return Result.ok(BeanConvertUtils.convert(
                projectService.update(workspaceId, code, body), ProjectResponse.class));
    }

    @PutMapping("/{code}/owner")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.UPDATE_OWNER, resourceType = AuditResourceType.PROJECT, resourceCode = "#projectCode")
    public Result<Void> updateOwner(@PathVariable Long workspaceId,
                                    @PathVariable Long code,
                                    @Valid @RequestBody ProjectOwnerUpdateRequest request) {
        projectService.updateOwner(workspaceId, code, request.getUserId());
        return Result.ok();
    }

    @DeleteMapping("/{code}")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.DELETE, resourceType = AuditResourceType.PROJECT, resourceCode = "#projectCode")
    public Result<Void> delete(@PathVariable Long workspaceId,
                               @PathVariable Long code) {
        projectService.delete(workspaceId, code);
        return Result.ok();
    }

    /**
     * 校验当前用户是项目创建者或 WORKSPACE_OWNER+（SUPER_ADMIN 在 PermissionInterceptor 已放行）。
     */
    private void checkProjectOwnerOrAdmin(Long workspaceId, Long projectCode) {
        UserContext.UserInfo user = UserContext.get();
        // WORKSPACE_OWNER 有权编辑任何项目
        if (RoleType.WORKSPACE_OWNER.name().equals(user.getRole())) {
            return;
        }
        // DEVELOPER 只能编辑自己创建的项目
        ProjectDTO project = projectService.getByCode(workspaceId, projectCode);
        if (!user.getUserId().equals(project.getCreatedBy())) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Only project creator or WORKSPACE_OWNER can update this project");
        }
    }
}
