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

import io.github.zzih.rudder.api.request.MemberAddRequest;
import io.github.zzih.rudder.api.request.WorkspaceCreateRequest;
import io.github.zzih.rudder.api.response.MemberResponse;
import io.github.zzih.rudder.api.response.WorkspaceResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.page.PageRequest;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.WorkspaceService;
import io.github.zzih.rudder.service.workspace.dto.MemberDTO;
import io.github.zzih.rudder.service.workspace.dto.WorkspaceDTO;

import java.util.List;

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
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final MemberService memberService;

    @PostMapping
    @RequireRole(RoleType.SUPER_ADMIN)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.CREATE, resourceType = AuditResourceType.WORKSPACE)
    public Result<WorkspaceResponse> create(@Valid @RequestBody WorkspaceCreateRequest request) {
        WorkspaceDTO body = BeanConvertUtils.convert(request, WorkspaceDTO.class);
        return Result.ok(BeanConvertUtils.convert(workspaceService.create(body), WorkspaceResponse.class));
    }

    @GetMapping
    public PageResult<WorkspaceResponse> list(@RequestParam(required = false) String searchVal,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        pageNum = PageRequest.normalizePageNum(pageNum);
        pageSize = PageRequest.normalizePageSize(pageSize);
        UserContext.UserInfo user = UserContext.get();
        IPage<WorkspaceDTO> page;
        if (user != null && RoleType.SUPER_ADMIN.name().equals(user.getRole())) {
            page = workspaceService.pageAll(searchVal, pageNum, pageSize);
        } else {
            Long userId = user != null ? user.getUserId() : null;
            page = workspaceService.pageByUserId(userId, searchVal, pageNum, pageSize);
        }
        return PageResult.of(BeanConvertUtils.convertList(page.getRecords(), WorkspaceResponse.class), page.getTotal(),
                pageNum, pageSize);
    }

    @GetMapping("/{id}")
    public Result<WorkspaceResponse> getById(@PathVariable Long id) {
        checkWorkspaceMember(id);
        return Result.ok(BeanConvertUtils.convert(workspaceService.getById(id), WorkspaceResponse.class));
    }

    @PutMapping("/{id}")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.UPDATE, resourceType = AuditResourceType.WORKSPACE, resourceCode = "#id")
    public Result<WorkspaceResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody WorkspaceCreateRequest request) {
        WorkspaceDTO body = BeanConvertUtils.convert(request, WorkspaceDTO.class);
        return Result.ok(BeanConvertUtils.convert(workspaceService.update(id, body), WorkspaceResponse.class));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.DELETE, resourceType = AuditResourceType.WORKSPACE, resourceCode = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        workspaceService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    public Result<List<MemberResponse>> listMembers(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convertList(memberService.listByWorkspaceId(id), MemberResponse.class));
    }

    @PostMapping("/{id}/members")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.ADD_MEMBER, resourceType = AuditResourceType.USER, resourceCode = "#request.userId")
    public Result<MemberResponse> addMember(@PathVariable Long id,
                                            @Valid @RequestBody MemberAddRequest request) {
        MemberDTO dto = memberService.addMember(id, request.getUserId(), request.getRole());
        return Result.ok(BeanConvertUtils.convert(dto, MemberResponse.class));
    }

    @PutMapping("/{id}/members/{userId}")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.UPDATE_MEMBER_ROLE, resourceType = AuditResourceType.USER, resourceCode = "#userId")
    public Result<Void> updateMemberRole(@PathVariable Long id,
                                         @PathVariable Long userId,
                                         @RequestBody MemberAddRequest request) {
        memberService.updateRole(id, userId, request.getRole());
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequireRole(RoleType.WORKSPACE_OWNER)
    @AuditLog(module = AuditModule.WORKSPACE, action = AuditAction.REMOVE_MEMBER, resourceType = AuditResourceType.USER, resourceCode = "#userId")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        memberService.removeMember(id, userId);
        return Result.ok();
    }

    /**
     * 校验当前用户是否为指定工作空间的成员（SUPER_ADMIN 直接放行）。
     */
    private void checkWorkspaceMember(Long workspaceId) {
        UserContext.UserInfo user = UserContext.get();
        if (RoleType.SUPER_ADMIN.name().equals(user.getRole())) {
            return;
        }
        if (memberService.getMember(workspaceId, user.getUserId()) == null) {
            throw new AuthException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER, workspaceId);
        }
    }
}
