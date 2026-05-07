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

import io.github.zzih.rudder.api.request.PasswordResetRequest;
import io.github.zzih.rudder.api.request.SuperAdminUpdateRequest;
import io.github.zzih.rudder.api.request.UserCreateRequest;
import io.github.zzih.rudder.api.request.UserEmailUpdateRequest;
import io.github.zzih.rudder.api.response.MemberResponse;
import io.github.zzih.rudder.api.response.UserResponse;
import io.github.zzih.rudder.api.response.UserSimpleResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.UserService;
import io.github.zzih.rudder.service.workspace.dto.UserDTO;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@RequireRole(RoleType.SUPER_ADMIN)
public class AdminUserController {

    private final UserService userService;
    private final MemberService memberService;

    @GetMapping
    public PageResult<UserResponse> list(@RequestParam(required = false) String searchVal,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        IPage<UserDTO> page = userService.pageDetail(searchVal, pageNum, pageSize);
        return PageResult.of(BeanConvertUtils.convertList(page.getRecords(), UserResponse.class),
                page.getTotal(), pageNum, pageSize);
    }

    @GetMapping("/simple")
    public Result<List<UserSimpleResponse>> listSimple() {
        return Result.ok(userService.listAllDetail().stream()
                .map(u -> new UserSimpleResponse(u.getId(), u.getUsername()))
                .toList());
    }

    @GetMapping("/{id}")
    public Result<UserResponse> getById(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convert(userService.getByIdDetail(id), UserResponse.class));
    }

    @PostMapping
    @AuditLog(module = AuditModule.USER, action = AuditAction.CREATE, resourceType = AuditResourceType.USER)
    public Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserDTO user = userService.createDetail(request.getUsername(), request.getPassword(), request.getEmail());
        return Result.ok(BeanConvertUtils.convert(user, UserResponse.class));
    }

    @PutMapping("/{id}/email")
    @AuditLog(module = AuditModule.USER, action = AuditAction.UPDATE_EMAIL, resourceType = AuditResourceType.USER, resourceCode = "#id")
    public Result<Void> updateEmail(@PathVariable Long id,
                                    @Valid @RequestBody UserEmailUpdateRequest request) {
        userService.updateEmail(id, request.getEmail());
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @AuditLog(module = AuditModule.USER, action = AuditAction.RESET_PASSWORD, resourceType = AuditResourceType.USER, resourceCode = "#id")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getPassword());
        return Result.ok();
    }

    @PutMapping("/{id}/super-admin")
    @AuditLog(module = AuditModule.USER, action = AuditAction.TOGGLE_SUPER_ADMIN, resourceType = AuditResourceType.USER, resourceCode = "#id")
    public Result<Void> toggleSuperAdmin(@PathVariable Long id,
                                         @Valid @RequestBody SuperAdminUpdateRequest request) {
        userService.updateSuperAdmin(id, Boolean.TRUE.equals(request.getIsSuperAdmin()));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = AuditModule.USER, action = AuditAction.DELETE, resourceType = AuditResourceType.USER, resourceCode = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/workspaces")
    public Result<List<MemberResponse>> listUserWorkspaces(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convertList(
                memberService.listByUserId(id), MemberResponse.class));
    }
}
