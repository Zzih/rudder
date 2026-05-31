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
import io.github.zzih.rudder.api.request.dataperm.DataPermBundleSaveRequest;
import io.github.zzih.rudder.api.request.dataperm.DataPermStatementRequest;
import io.github.zzih.rudder.api.request.dataperm.GrantRevokeRequest;
import io.github.zzih.rudder.api.response.UserSimpleResponse;
import io.github.zzih.rudder.api.response.WorkspaceGrantResponse;
import io.github.zzih.rudder.api.response.dataperm.AggregatedGrantResponse;
import io.github.zzih.rudder.api.response.dataperm.AggregatedUserGrantsResponse;
import io.github.zzih.rudder.api.response.dataperm.DataPermBundleResponse;
import io.github.zzih.rudder.api.response.dataperm.DataPermPermissionItemResponse;
import io.github.zzih.rudder.api.response.dataperm.DataPermStatementResponse;
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
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.enums.workspace.WorkspaceResourceType;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.UserGrantsDTO;
import io.github.zzih.rudder.service.dataperm.service.BundleService;
import io.github.zzih.rudder.service.dataperm.service.BundleStatementService;
import io.github.zzih.rudder.service.dataperm.service.DataPermApplyService;
import io.github.zzih.rudder.service.dataperm.service.GrantService;
import io.github.zzih.rudder.service.permission.WorkspacePermissionService;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.UserService;
import io.github.zzih.rudder.service.workspace.WorkspaceService;
import io.github.zzih.rudder.service.workspace.dto.UserDTO;
import io.github.zzih.rudder.service.workspace.dto.WorkspaceDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.baomidou.mybatisplus.core.metadata.IPage;

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
    private final BundleService bundleService;
    private final BundleStatementService bundleStatementService;
    private final DataPermConfigService dataPermConfigService;
    private final UserService userService;
    private final MemberService memberService;
    private final WorkspaceService workspaceService;
    private final WorkspacePermissionService workspacePermissionService;

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
                request.getBundleIds() == null ? List.of() : request.getBundleIds(),
                BeanConvertUtils.convertListViaJson(request.getDirectGrants(), DataPermStatementDTO.class),
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

    /** 用户必须当前持有该 role,否则 404。以资源行为单位分页该 role 的库表行。 */
    @GetMapping("/my-grants/bundles/{bundleId}/permissions")
    @RequireLoggedIn
    public PageResult<DataPermPermissionItemResponse> myGrantsRolePermissions(
                                                                              @PathVariable Long bundleId,
                                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                                              @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return PageResult.of(grantService.pageGrantItems(userId, bundleId, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermPermissionItemResponse.class));
    }

    @GetMapping("/my-grants/direct/permissions")
    @RequireLoggedIn
    public PageResult<DataPermPermissionItemResponse> myGrantsDirectPermissions(
                                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                                @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long userId = UserContext.requireUserId();
        return PageResult.of(grantService.pageGrantItems(userId, null, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermPermissionItemResponse.class));
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

    @GetMapping("/bundles")
    @RequireLoggedIn
    public Result<List<DataPermBundleResponse>> listBundles() {
        requireEnabled();
        return Result.ok(BeanConvertUtils.convertListViaJson(
                bundleService.listVisibleToWorkspace(UserContext.getWorkspaceId()), DataPermBundleResponse.class));
    }

    @GetMapping("/bundles/page")
    @RequireSuperAdmin
    public PageResult<DataPermBundleResponse> pageBundles(
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        return PageResult.of(bundleService.listPage(keyword, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermBundleResponse.class));
    }

    @GetMapping("/bundles/{id}")
    @RequireSuperAdmin
    public Result<DataPermBundleResponse> getRole(@PathVariable Long id) {
        requireEnabled();
        return Result.ok(BeanConvertUtils.convertViaJson(bundleService.get(id), DataPermBundleResponse.class));
    }

    /** 列出某资源包已授权(可见)的工作空间(id + name)。 */
    @GetMapping("/bundles/{id}/workspaces")
    @RequireSuperAdmin
    public Result<List<WorkspaceGrantResponse>> listBundleWorkspaces(@PathVariable Long id) {
        requireEnabled();
        bundleService.get(id);
        List<WorkspaceDTO> wss = workspaceService.listByIds(
                workspacePermissionService.listGrantedWorkspaceIds(WorkspaceResourceType.DATA_PERM_BUNDLE, id));
        return Result.ok(wss.stream()
                .map(w -> new WorkspaceGrantResponse(w.getId(), w.getName()))
                .toList());
    }

    /** 用 workspaceIds 全量覆盖资源包的可见工作空间集合(幂等)。 */
    @PutMapping("/bundles/{id}/workspaces")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "更新权限包工作空间可见性")
    public Result<Void> setBundleWorkspaces(@PathVariable Long id, @RequestBody List<Long> workspaceIds) {
        requireEnabled();
        bundleService.get(id);
        Set<Long> ids = workspaceIds == null ? new HashSet<>() : new HashSet<>(workspaceIds);
        workspacePermissionService.setGrants(WorkspaceResourceType.DATA_PERM_BUNDLE, id, ids, UserContext.getUserId());
        return Result.ok();
    }

    @PostMapping("/bundles")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.CREATE, resourceType = AuditResourceType.DATA_PERM_ROLE, description = "创建数据资源包")
    public Result<Long> createRole(@Valid @RequestBody DataPermBundleSaveRequest request) {
        requireEnabled();
        return Result.ok(bundleService.create(request.getName(), request.getDescription()));
    }

    @PutMapping("/bundles/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "更新数据资源包元信息")
    public Result<Void> updateRoleMeta(@PathVariable Long id,
                                       @Valid @RequestBody DataPermBundleSaveRequest request) {
        requireEnabled();
        bundleService.updateMeta(id, request.getName(), request.getDescription());
        return Result.ok();
    }

    // -------- 权限包内「作用域块」CRUD --------

    // 只读:既给 SuperAdmin 编辑权限包用,也给申请人在申请弹窗预览包内权限项用(块定义是元数据,非数据本身)。
    // 后端分页 + keyword 后端搜索(作用域名 / 分组名 / 库表路径)。
    @GetMapping("/bundles/{id}/statements")
    @RequireLoggedIn
    public PageResult<DataPermStatementResponse> listBundleStatements(
                                                                      @PathVariable Long id,
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                                      @RequestParam(defaultValue = "10") int pageSize) {
        requireEnabled();
        // 申请人只能预览当前工作空间可见的包;SuperAdmin 走包管理需查看全部,放行。不可见按 NOT_FOUND 不泄露存在性。
        if (!UserContext.isSuperAdmin()
                && !workspacePermissionService.hasPermission(
                        WorkspaceResourceType.DATA_PERM_BUNDLE, id, UserContext.getWorkspaceId())) {
            throw new NotFoundException(DataPermErrorCode.ROLE_NOT_FOUND, id);
        }
        return PageResult.of(bundleStatementService.pageStatements(id, keyword, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermStatementResponse.class));
    }

    @PostMapping("/bundles/{id}/statements")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.CREATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "新增资源包作用域块")
    public Result<Long> addBundleStatement(@PathVariable Long id,
                                           @Valid @RequestBody DataPermStatementRequest request) {
        requireEnabled();
        return Result.ok(bundleStatementService.addStatement(id,
                BeanConvertUtils.convertViaJson(request, DataPermStatementDTO.class)));
    }

    @PutMapping("/bundles/{id}/statements/{statementId}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "更新资源包作用域块")
    public Result<Void> updateBundleStatement(@PathVariable Long id,
                                              @PathVariable Long statementId,
                                              @Valid @RequestBody DataPermStatementRequest request) {
        requireEnabled();
        bundleStatementService.updateStatement(id, statementId,
                BeanConvertUtils.convertViaJson(request, DataPermStatementDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/bundles/{id}/statements/{statementId}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "删除资源包作用域块")
    public Result<Void> deleteBundleStatement(@PathVariable Long id, @PathVariable Long statementId) {
        requireEnabled();
        bundleStatementService.deleteStatement(id, statementId);
        return Result.ok();
    }

    @DeleteMapping("/bundles/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_ROLE, resourceCode = "#id", description = "删除数据资源包(级联失效引用 grants)")
    public Result<Integer> deleteRole(@PathVariable Long id) {
        requireEnabled();
        return Result.ok(bundleService.delete(id));
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

    /** 「按用户」聚合视图:分页列出用户当前持有的权限包 + 直接授权(含权限项明细)。workspace 范围同明细快照。 */
    @GetMapping("/admin/grants/aggregated")
    @RequireLoggedIn
    public PageResult<AggregatedUserGrantsResponse> pageAggregatedGrants(
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf,
                                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                                         @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long ws = UserContext.getWorkspaceIdOrNull();
        Collection<Long> restrict = ws == null ? null : memberService.listUserIdsByWorkspace(ws);
        LocalDateTime t = asOf == null ? LocalDateTime.now() : asOf;
        IPage<UserGrantsDTO> page = grantService.pageActiveGrantsByUser(restrict, t, pageNum, pageSize);
        Map<Long, String> nameById = userService.listByIds(
                page.getRecords().stream().map(UserGrantsDTO::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserDTO::getId, UserDTO::getUsername));
        return PageResult.of(page, dto -> {
            AggregatedUserGrantsResponse r = new AggregatedUserGrantsResponse();
            r.setUserId(dto.getUserId());
            r.setUsername(nameById.getOrDefault(dto.getUserId(), "user#" + dto.getUserId()));
            r.setGrants(BeanConvertUtils.convertListViaJson(dto.getGrants(), AggregatedGrantResponse.class));
            return r;
        });
    }

    /**
     * 聚合视图展开某权限包 / 直接授权时,分页拉取其权限项(当前态)。
     * {@code bundleId} 非空 = 该权限包;为空 = 该用户全部直接授权。workspace 范围同明细快照。
     */
    @GetMapping("/admin/grants/items")
    @RequireLoggedIn
    public PageResult<DataPermPermissionItemResponse> pageGrantItems(
                                                                     @RequestParam Long userId,
                                                                     @RequestParam(required = false) Long bundleId,
                                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                                     @RequestParam(defaultValue = "20") int pageSize) {
        requireEnabled();
        Long ws = UserContext.getWorkspaceIdOrNull();
        if (ws != null && !memberService.isMember(ws, userId)) {
            throw new AuthException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER, userId);
        }
        return PageResult.of(grantService.pageGrantItems(userId, bundleId, pageNum, pageSize),
                dto -> BeanConvertUtils.convertViaJson(dto, DataPermPermissionItemResponse.class));
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

    @PostMapping("/admin/grants/bundle/{id}/revoke")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATA_PERM, action = AuditAction.DELETE, resourceType = AuditResourceType.DATA_PERM_GRANT, resourceCode = "#id", description = "撤销 role grant")
    public Result<Boolean> revokeBundleGrant(@PathVariable Long id,
                                             @RequestBody(required = false) GrantRevokeRequest body) {
        requireEnabled();
        boolean changed = grantService.revokeBundleGrant(
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
