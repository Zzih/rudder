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

import io.github.zzih.rudder.api.request.dataperm.DataPermApplyRequest;
import io.github.zzih.rudder.api.request.dataperm.DataPermRolePermissionItemRequest;
import io.github.zzih.rudder.api.request.dataperm.DataPermRoleSaveRequest;
import io.github.zzih.rudder.api.request.dataperm.GrantRevokeRequest;
import io.github.zzih.rudder.api.response.UserSimpleResponse;
import io.github.zzih.rudder.api.response.dataperm.DataPermRolePermissionItemResponse;
import io.github.zzih.rudder.api.response.dataperm.DataPermRoleResponse;
import io.github.zzih.rudder.api.response.dataperm.EffectiveSnapshotRowResponse;
import io.github.zzih.rudder.api.response.dataperm.MyGrantsSummaryResponse;
import io.github.zzih.rudder.api.response.dataperm.UserGrantViewResponse;
import io.github.zzih.rudder.api.security.annotation.RequireLoggedIn;
import io.github.zzih.rudder.api.security.annotation.RequireSuperAdmin;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRolePermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.service.DataPermApplyService;
import io.github.zzih.rudder.service.dataperm.service.GrantService;
import io.github.zzih.rudder.service.dataperm.service.RolePermissionService;
import io.github.zzih.rudder.service.dataperm.service.RoleService;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.UserService;
import io.github.zzih.rudder.service.workspace.dto.UserDTO;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 数据权限对外 HTTP 入口:
 * <ul>
 *   <li>用户:提交申请、查我的权限</li>
 *   <li>权限包(role)管理:CRUD + 替换 permissions(SuperAdmin)</li>
 *   <li>数据权限总览:LoggedIn,非 SUPER_ADMIN 自动按 {@link UserContext#getWorkspaceIdOrNull()} 限定可见 user;撤销仅 SuperAdmin</li>
 * </ul>
 *
 * <p>权限通过方法级 {@link RequireLoggedIn} / {@link RequireSuperAdmin} 区分,
 * 避免类级注解 + 单点 override 的混淆。
 *
 * <p>**DTO ↔ Response**:走 {@code BeanConvertUtils.convertViaJson} 让 Jackson 自动桥接
 * enum→String 与跨包嵌套 list(同构镜像)。
 */
@RestController
@RequestMapping("/api/data-perm")
@RequiredArgsConstructor
public class DataPermController {

    private final DataPermApplyService applyService;
    private final GrantService grantService;
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final DataPermConfigService dataPermConfigService;
    private final UserService userService;
    private final MemberService memberService;

    /** enabled=false 时所有业务端点返 503,避免在功能关停时静默落数据。 */
    private void requireEnabled() {
        if (!dataPermConfigService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "data perm disabled");
        }
    }

    // ==================== 用户:申请 / 我的权限 ====================

    @PostMapping("/applications")
    @RequireLoggedIn
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.CREATE, resourceType = AuditResourceType.APPROVAL_RECORD, description = "提交数据权限申请")
    public Result<Long> submitApplication(@Valid @RequestBody DataPermApplyRequest request) {
        requireEnabled();
        Long approvalId = applyService.submit(
                request.getRoleIds() == null ? List.of() : request.getRoleIds(),
                BeanConvertUtils.convertList(request.getDirectItems(), DataPermRolePermissionItemDTO.class),
                request.getExpireAt(),
                request.getReason());
        return Result.ok(approvalId);
    }

    @GetMapping("/my-grants/summary")
    @RequireLoggedIn
    public Result<MyGrantsSummaryResponse> myGrantsSummary() {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return Result.ok(BeanConvertUtils.convertViaJson(
                grantService.summary(userId), MyGrantsSummaryResponse.class));
    }

    /** 用户必须当前持有该 role,否则 404。 */
    @GetMapping("/my-grants/roles/{roleId}/permissions")
    @RequireLoggedIn
    public PageResult<DataPermRolePermissionItemResponse> myGrantsRolePermissions(
                                                                                  @PathVariable Long roleId,
                                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                                  @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return PageResult.of(
                grantService.pagePermissionsForUserRole(userId, roleId, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermRolePermissionItemResponse.class));
    }

    @GetMapping("/my-grants/direct/permissions")
    @RequireLoggedIn
    public PageResult<DataPermRolePermissionItemResponse> myGrantsDirectPermissions(
                                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return PageResult.of(grantService.pageDirectPermissions(userId, pageNum, pageSize),
                DataPermRolePermissionItemResponse.class);
    }

    @GetMapping("/my-grants/history")
    @RequireLoggedIn
    public Result<List<UserGrantViewResponse>> myGrantsHistory() {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return Result.ok(BeanConvertUtils.convertListViaJson(
                grantService.listInactiveByUser(userId), UserGrantViewResponse.class));
    }

    // ==================== 资源包(role)管理 ====================

    @GetMapping("/roles")
    @RequireLoggedIn
    public Result<List<DataPermRoleResponse>> listRoles() {
        requireEnabled();
        return Result.ok(BeanConvertUtils.convertListViaJson(
                roleService.listAll(), DataPermRoleResponse.class));
    }

    @GetMapping("/roles/page")
    @RequireSuperAdmin
    public PageResult<DataPermRoleResponse> pageRoles(
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        return PageResult.of(roleService.listPage(keyword, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermRoleResponse.class));
    }

    @GetMapping("/roles/{id}")
    @RequireSuperAdmin
    public Result<DataPermRoleResponse> getRole(@PathVariable Long id) {
        requireEnabled();
        return Result.ok(BeanConvertUtils.convertViaJson(roleService.get(id), DataPermRoleResponse.class));
    }

    @PostMapping("/roles")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.CREATE, resourceType = AuditResourceType.DATA_PERM_ROLE, description = "创建数据资源包")
    public Result<Long> createRole(@Valid @RequestBody DataPermRoleSaveRequest request) {
        requireEnabled();
        return Result.ok(roleService.create(request.getName(), request.getDescription()));
    }

    @PutMapping("/roles/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "更新数据资源包元信息")
    public Result<Void> updateRoleMeta(@PathVariable Long id,
                                       @Valid @RequestBody DataPermRoleSaveRequest request) {
        requireEnabled();
        roleService.updateMeta(id, request.getName(), request.getDescription());
        return Result.ok();
    }

    // -------- 权限项行级 CRUD --------

    @GetMapping("/roles/{id}/permissions/page")
    @RequireSuperAdmin
    public PageResult<DataPermRolePermissionItemResponse> pageRolePermissions(
                                                                              @PathVariable Long id,
                                                                              @RequestParam(required = false) String keyword,
                                                                              @RequestParam(required = false) PluginType pluginType,
                                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                                              @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        return PageResult.of(rolePermissionService.page(id, keyword, pluginType, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermRolePermissionItemResponse.class));
    }

    @PostMapping("/roles/{id}/permissions")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.CREATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "新增资源包权限项")
    public Result<Long> addRolePermission(@PathVariable Long id,
                                          @Valid @RequestBody DataPermRolePermissionItemRequest request) {
        requireEnabled();
        return Result.ok(rolePermissionService.add(id,
                BeanConvertUtils.convert(request, DataPermRolePermissionItemDTO.class)));
    }

    @PutMapping("/roles/{id}/permissions/{permId}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "更新资源包权限项")
    public Result<Void> updateRolePermission(@PathVariable Long id,
                                             @PathVariable Long permId,
                                             @Valid @RequestBody DataPermRolePermissionItemRequest request) {
        requireEnabled();
        rolePermissionService.update(id, permId,
                BeanConvertUtils.convert(request, DataPermRolePermissionItemDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/roles/{id}/permissions/{permId}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "删除资源包权限项")
    public Result<Void> deleteRolePermission(@PathVariable Long id, @PathVariable Long permId) {
        requireEnabled();
        rolePermissionService.delete(id, permId);
        return Result.ok();
    }

    @DeleteMapping("/roles/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "删除数据资源包(级联失效引用 grants)")
    public Result<Integer> deleteRole(@PathVariable Long id) {
        requireEnabled();
        return Result.ok(roleService.delete(id));
    }

    // ==================== 数据权限总览(全员可见,按 workspace 限定) ====================

    /** workspace 范围由 {@link UserContext#getWorkspaceIdOrNull()} 决定:SUPER_ADMIN 返 null 看全平台,其余按当前 workspace 限定。 */
    @GetMapping("/admin/effective-snapshot")
    @RequireLoggedIn
    public PageResult<EffectiveSnapshotRowResponse> pageEffectiveSnapshot(
                                                                          @RequestParam(required = false) List<Long> userIds,
                                                                          @RequestParam(required = false) List<Long> scopeCodes,
                                                                          @RequestParam(required = false) String keyword,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf,
                                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                                          @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        // sources 是嵌套 Source 列表,DTO / Response 内部类跨包,浅拷贝会静默跳过,必须走 JSON 桥接
        return PageResult.of(
                grantService.pageEffectiveSnapshot(userIds, scopeCodes, keyword, asOf,
                        UserContext.getWorkspaceIdOrNull(), pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, EffectiveSnapshotRowResponse.class));
    }

    @GetMapping("/admin/grants/by-user/{userId}")
    @RequireLoggedIn
    public Result<List<UserGrantViewResponse>> listGrantsByUser(
                                                                @PathVariable Long userId,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        requireEnabled();
        Long ws = UserContext.getWorkspaceIdOrNull();
        if (ws != null && !memberService.isMember(ws, userId)) {
            throw new AuthException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER, userId);
        }
        LocalDateTime t = asOf == null ? LocalDateTime.now() : asOf;
        return Result.ok(BeanConvertUtils.convertListViaJson(
                grantService.listActiveByUserAt(userId, t), UserGrantViewResponse.class));
    }

    @GetMapping("/admin/users/search")
    @RequireLoggedIn
    public Result<List<UserSimpleResponse>> searchUsers(@RequestParam(required = false) String keyword) {
        requireEnabled();
        Long ws = UserContext.getWorkspaceIdOrNull();
        List<UserDTO> users;
        if (ws == null) {
            users = userService.pageDetail(keyword, 1, 20).getRecords();
        } else {
            List<Long> memberIds = memberService.listUserIdsByWorkspace(ws);
            String kw = keyword == null ? "" : keyword.toLowerCase();
            users = userService.listByIds(memberIds).stream()
                    .filter(u -> kw.isEmpty() || u.getUsername().toLowerCase().contains(kw))
                    .limit(20)
                    .toList();
        }
        return Result.ok(users.stream()
                .map(u -> new UserSimpleResponse(u.getId(), u.getUsername()))
                .toList());
    }

    @PostMapping("/admin/grants/role/{id}/revoke")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_GRANT, resourceCode = "#id", description = "撤销 role grant")
    public Result<Boolean> revokeRoleGrant(@PathVariable Long id,
                                           @RequestBody(required = false) GrantRevokeRequest body) {
        requireEnabled();
        boolean changed = grantService.revokeRoleGrant(
                id, UserContext.requireUserId(),
                body != null ? body.getNote() : null);
        return Result.ok(changed);
    }

    @PostMapping("/admin/grants/direct/{id}/revoke")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_GRANT, resourceCode = "#id", description = "撤销 direct grant")
    public Result<Boolean> revokeDirectGrant(@PathVariable Long id,
                                             @RequestBody(required = false) GrantRevokeRequest body) {
        requireEnabled();
        boolean changed = grantService.revokeDirectGrant(
                id, UserContext.requireUserId(),
                body != null ? body.getNote() : null);
        return Result.ok(changed);
    }

    @PostMapping("/admin/grants/by-approval/{id}/revoke")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_GRANT, resourceCode = "#id", description = "按申请单批量撤销 grants")
    public Result<Integer> revokeGrantsByApproval(@PathVariable Long id,
                                                  @RequestBody(required = false) GrantRevokeRequest body) {
        requireEnabled();
        int count = grantService.revokeByApproval(
                id, UserContext.requireUserId(),
                body != null ? body.getNote() : null);
        return Result.ok(count);
    }
}
