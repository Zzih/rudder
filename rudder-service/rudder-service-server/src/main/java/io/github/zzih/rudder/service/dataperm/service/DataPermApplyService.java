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
import io.github.zzih.rudder.common.enums.workspace.WorkspaceResourceType;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermApplyContext;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.ResourcePathDTO;
import io.github.zzih.rudder.service.permission.WorkspacePermissionService;
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
 *   <li>校验:至少一个 bundleId 或一个 direct 项;到期日不早于当下</li>
 *   <li>校验 bundleIds 都存在</li>
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
    private final DataPermBundleDao bundleDao;
    private final DataPermConfigService configService;
    private final DataPermScopeAccessGroupService accessGroupService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final WorkspacePermissionService workspacePermissionService;

    /**
     * 提交数据权限申请。返回审批单 id。
     *
     * @param bundleIds       申请的资源包 ids,可空(此时 directGrants 必须非空)
     * @param directGrants  申请的 direct 作用域块,可空(此时 bundleIds 必须非空)
     * @param expireAt      到期日,null=永久
     * @param reason        申请理由(必填)
     */
    public Long submit(List<Long> bundleIds,
                       List<DataPermStatementDTO> directGrants,
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
        validateContent(bundleIds, directGrants, expireAt);

        if (bundleIds != null) {
            for (Long bundleId : bundleIds) {
                // 申请侧只能申请当前工作空间可见的权限包;不可见与不存在统一按 NOT_FOUND,
                // 既不泄露存在性,也堵死「枚举 id 越权申请其他空间的包」。
                if (!workspacePermissionService.hasPermission(
                        WorkspaceResourceType.DATA_PERM_BUNDLE, bundleId, workspaceId)) {
                    throw new NotFoundException(DataPermErrorCode.ROLE_NOT_FOUND, bundleId);
                }
            }
        }

        if (directGrants != null) {
            for (DataPermStatementDTO block : directGrants) {
                if (block.getScopeCode() == null) {
                    throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "block.scopeCode required");
                }
                if (configService.findScope(block.getScopeCode()).isEmpty()) {
                    throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING,
                            "scope:" + block.getScopeCode());
                }
                // groupIds 全部存在且属于该 scope,否则抛 ACCESS_GROUP_*
                accessGroupService.validateGroups(block.getScopeCode(), block.getGroupIds());
                if (block.getResources() == null || block.getResources().isEmpty()) {
                    throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "block.resources required");
                }
                // 四层全空的库表行物化时每个适用层都会展开成 "*",等于整 scope 授权;与权限包录入口径一致地拒绝。
                for (ResourcePathDTO r : block.getResources()) {
                    if (DataPermStatementSupport.normLevel(r.getCatalogNames()).isEmpty()
                            && DataPermStatementSupport.normLevel(r.getDatabaseNames()).isEmpty()
                            && DataPermStatementSupport.normLevel(r.getTableNames()).isEmpty()
                            && DataPermStatementSupport.normLevel(r.getColumnNames()).isEmpty()) {
                        throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "resource path empty");
                    }
                }
            }
        }

        DataPermApplyContext context = new DataPermApplyContext(
                applicantId,
                workspaceId,
                bundleIds == null ? List.of() : List.copyOf(bundleIds),
                directGrants == null ? List.of() : List.copyOf(directGrants),
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
        log.info("Data perm application submitted: approvalId={}, applicant={}, roles={}, directGrants={}",
                approvalId, applicantId,
                context.bundleIds().size(), context.directGrants().size());
        return approvalId;
    }

    private static void validateContent(List<Long> bundleIds,
                                        List<DataPermStatementDTO> directGrants,
                                        LocalDateTime expireAt) {
        boolean hasRoles = bundleIds != null && !bundleIds.isEmpty();
        boolean hasDirect = directGrants != null && !directGrants.isEmpty();
        if (!hasRoles && !hasDirect) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "at least one of bundleIds / directGrants required");
        }
        if (expireAt != null && expireAt.isBefore(LocalDateTime.now())) {
            throw new BizException(DataPermErrorCode.EXPIRE_DATE_INVALID, expireAt.toString());
        }
    }

    private static String buildTitle(DataPermApplyContext context) {
        int parts = context.bundleIds().size() + context.directGrants().size();
        return I18n.t("msg.dataperm.applyTitle", parts);
    }

    private String buildContent(DataPermApplyContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("msg.dataperm.applyContent.header",
                resolveUsername(context.applicantUserId()),
                resolveWorkspaceName(context.applicantWorkspaceId()))).append('\n');
        if (!context.bundleIds().isEmpty()) {
            sb.append(I18n.t("msg.dataperm.applyContent.roles",
                    String.join(", ", resolveBundleNames(context.bundleIds())))).append('\n');
        }
        if (!context.directGrants().isEmpty()) {
            sb.append(I18n.t("msg.dataperm.applyContent.directHeader", context.directGrants().size())).append('\n');
            Map<Long, String> groupNameById = accessGroupService.allNames();
            for (DataPermStatementDTO block : context.directGrants()) {
                String groups = String.join(",",
                        DataPermScopeAccessGroupService.resolveNames(block.getGroupIds(), groupNameById));
                for (ResourcePathDTO r : block.getResources()) {
                    sb.append(I18n.t("msg.dataperm.applyContent.directLine",
                            resolveScopeName(block.getScopeCode()), resourcePathLabel(r), groups)).append('\n');
                }
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

    private List<String> resolveBundleNames(List<Long> bundleIds) {
        Map<Long, String> nameById = bundleDao.selectByIds(bundleIds).stream()
                .collect(Collectors.toMap(DataPermBundle::getId, DataPermBundle::getName));
        return bundleIds.stream()
                .map(id -> nameById.getOrDefault(id, "role#" + id))
                .toList();
    }

    private String resolveScopeName(Long code) {
        if (code == null) {
            return "?";
        }
        return configService.findScope(code)
                .map(DataPermScopeDTO::getName)
                .orElse("scope#" + code);
    }

    /** 一条库表行的可读路径:各层值用 "." 连接,某层多值显示 {@code [a,b]},空层跳过。 */
    private static String resourcePathLabel(ResourcePathDTO r) {
        return java.util.stream.Stream.of(
                r.getCatalogNames(), r.getDatabaseNames(), r.getTableNames(), r.getColumnNames())
                .filter(v -> v != null && !v.isEmpty())
                .map(v -> v.size() == 1 ? v.get(0) : "[" + String.join(",", v) + "]")
                .collect(Collectors.joining("."));
    }
}
