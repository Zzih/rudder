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

package io.github.zzih.rudder.service.dataperm.service;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermRoleDao;
import io.github.zzih.rudder.dao.entity.DataPermRole;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermApplyContext;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRolePermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.workflow.ApprovalService;
import io.github.zzih.rudder.service.workspace.UserService;
import io.github.zzih.rudder.service.workspace.WorkspaceService;
import io.github.zzih.rudder.service.workspace.dto.WorkspaceDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据权限申请提交编排:
 * <ol>
 *   <li>校验:至少一个 roleId 或一个 direct 项;到期日不早于当下</li>
 *   <li>校验 roleIds 都存在</li>
 *   <li>校验 direct 项的数据源都在申请人当前工作空间已开放</li>
 *   <li>组装 {@link DataPermApplyContext} → 序列化进 {@code ApprovalRequest.extra},
 *       由 {@code ApprovalService.submit} 持久化到 {@code t_r_approval_record.ext_data}</li>
 *   <li>调 {@link ApprovalService#submit} 创建审批单,resourceType={@code DATA_PERM_APPLY}</li>
 * </ol>
 *
 * <p>**业务表不动**:申请通过前不创建 grants;通过事件由
 * {@code DataPermApprovalIntegration.onFinalized} 反查 ext_data 后写 grants。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermApplyService {

    /** {@code request.extra} 中携带申请结构化数据的 key。 */
    public static final String EXTRA_KEY_DATA_PERM = "dataPerm";

    private static final DateTimeFormatter EXPIRE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApprovalService approvalService;
    private final DataPermRoleDao roleDao;
    private final DataPermConfigService configService;
    private final UserService userService;
    private final WorkspaceService workspaceService;

    /**
     * 提交数据权限申请。返回审批单 id。
     *
     * @param roleIds       申请的资源包 ids,可空(此时 directItems 必须非空)
     * @param directItems   申请的 direct 项,可空(此时 roleIds 必须非空)
     * @param expireAt      到期日,null=永久
     * @param reason        申请理由(必填)
     */
    public Long submit(List<Long> roleIds,
                       List<DataPermRolePermissionItemDTO> directItems,
                       LocalDateTime expireAt,
                       String reason) {
        Long applicantId = UserContext.requireUserId();
        Long workspaceId = UserContext.getWorkspaceId();
        if (workspaceId == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "workspaceId required");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "reason required");
        }
        validateContent(roleIds, directItems, expireAt);

        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleDao.selectById(roleId) == null) {
                    throw new NotFoundException(DataPermErrorCode.ROLE_NOT_FOUND, roleId);
                }
            }
        }

        if (directItems != null) {
            for (DataPermRolePermissionItemDTO item : directItems) {
                if (item.getScopeCode() == null) {
                    throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                            "directItem.scopeCode required");
                }
                if (configService.findScope(item.getScopeCode()).isEmpty()) {
                    throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING,
                            "scope:" + item.getScopeCode());
                }
                if (item.getAccesses() == null || item.getAccesses().isEmpty()) {
                    throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                            "directItem.accesses required");
                }
            }
        }

        DataPermApplyContext context = new DataPermApplyContext(
                applicantId,
                workspaceId,
                roleIds == null ? List.of() : List.copyOf(roleIds),
                directItems == null ? List.of() : List.copyOf(directItems),
                expireAt);
        Map<String, String> extra = new HashMap<>();
        extra.put(EXTRA_KEY_DATA_PERM, JsonUtils.toJson(context));

        String title = buildTitle(context);
        String content = buildContent(context);
        ApprovalRequest request = ApprovalRequest.builder()
                .title(title)
                .content(content)
                .extra(extra)
                .build();

        // resourceCode 取申请人 user_id —— 被授权的对象就是该用户
        Long approvalId = approvalService.submit(
                request,
                ApprovalResourceType.DATA_PERM_APPLY,
                applicantId,
                workspaceId,
                null,
                reason);
        log.info("Data perm application submitted: approvalId={}, applicant={}, roles={}, directItems={}",
                approvalId, applicantId,
                context.roleIds().size(), context.directItems().size());
        return approvalId;
    }

    private static void validateContent(List<Long> roleIds,
                                        List<DataPermRolePermissionItemDTO> directItems,
                                        LocalDateTime expireAt) {
        boolean hasRoles = roleIds != null && !roleIds.isEmpty();
        boolean hasDirect = directItems != null && !directItems.isEmpty();
        if (!hasRoles && !hasDirect) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "at least one of roleIds / directItems required");
        }
        if (expireAt != null && expireAt.isBefore(LocalDateTime.now())) {
            throw new BizException(DataPermErrorCode.EXPIRE_DATE_INVALID, expireAt.toString());
        }
    }

    private static String buildTitle(DataPermApplyContext context) {
        int parts = context.roleIds().size() + context.directItems().size();
        return I18n.t("msg.dataperm.applyTitle", parts);
    }

    private String buildContent(DataPermApplyContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("msg.dataperm.applyContent.header",
                resolveUsername(context.applicantUserId()),
                resolveWorkspaceName(context.applicantWorkspaceId()))).append('\n');
        if (!context.roleIds().isEmpty()) {
            sb.append(I18n.t("msg.dataperm.applyContent.roles",
                    String.join(", ", resolveRoleNames(context.roleIds())))).append('\n');
        }
        if (!context.directItems().isEmpty()) {
            sb.append(I18n.t("msg.dataperm.applyContent.directHeader", context.directItems().size())).append('\n');
            for (DataPermRolePermissionItemDTO item : context.directItems()) {
                sb.append(I18n.t("msg.dataperm.applyContent.directLine",
                        resolveServiceName(item.getScopeCode()),
                        resourcePath(item),
                        String.join(",", item.getAccesses()))).append('\n');
            }
        }
        String expire = context.expireAt() == null
                ? I18n.t("msg.dataperm.permanent")
                : context.expireAt().format(EXPIRE_AT_FORMATTER);
        sb.append(I18n.t("msg.dataperm.applyContent.expireAt", expire));
        return sb.toString();
    }

    private String resolveUsername(Long userId) {
        User u = userService.getById(userId);
        return u == null ? ("user#" + userId) : u.getUsername();
    }

    private String resolveWorkspaceName(Long workspaceId) {
        WorkspaceDTO w = workspaceService.getById(workspaceId);
        return w == null ? ("workspace#" + workspaceId) : w.getName();
    }

    private List<String> resolveRoleNames(List<Long> roleIds) {
        Map<Long, String> nameById = roleDao.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(DataPermRole::getId, DataPermRole::getName));
        return roleIds.stream()
                .map(id -> nameById.getOrDefault(id, "role#" + id))
                .toList();
    }

    private String resolveServiceName(Long code) {
        if (code == null) {
            return "?";
        }
        return configService.findScope(code)
                .map(DataPermScopeDTO::getName)
                .orElse("service#" + code);
    }

    private static String resourcePath(DataPermRolePermissionItemDTO item) {
        return java.util.stream.Stream.of(
                item.getCatalogName(), item.getDatabaseName(),
                item.getTableName(), item.getColumnName())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("."));
    }
}
