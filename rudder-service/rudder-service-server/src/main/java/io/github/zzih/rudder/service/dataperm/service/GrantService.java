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

import io.github.zzih.rudder.common.enums.dataperm.DataPermGrantEndReason;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermRoleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserEffectiveSnapshotDao;
import io.github.zzih.rudder.dao.dao.DataPermUserRoleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermRole;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.entity.DataPermUserRoleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantDetailView;
import io.github.zzih.rudder.dao.entity.view.DataPermUserRoleGrantDetailView;
import io.github.zzih.rudder.dao.projection.EffectiveSnapshotRow;
import io.github.zzih.rudder.dao.projection.UserDirectGrantOverviewRow;
import io.github.zzih.rudder.dao.projection.UserRoleGrantSummaryRow;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRolePermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.dto.EffectiveSnapshotRowDTO;
import io.github.zzih.rudder.service.dataperm.dto.MyGrantsSummaryDTO;
import io.github.zzih.rudder.service.dataperm.dto.UserGrantViewDTO;
import io.github.zzih.rudder.service.dataperm.reconciler.DataPermReconciler;
import io.github.zzih.rudder.service.dataperm.reconciler.PermSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户权限事实(grants) 的查询与撤销:
 * <ul>
 *   <li>{@link #listActiveByUserAt}:用户当前生效的 grants(按来源组织成卡片)</li>
 *   <li>{@link #revokeRoleGrant} / {@link #revokeDirectGrant}:单条撤销</li>
 *   <li>{@link #revokeByApproval}:按 approval 批量撤销</li>
 * </ul>
 *
 * <p>"按来源" = ROLE 卡片(每个 role 一张,展示 role 当前的全部 permission)+
 * DIRECT 卡片(每条 direct grant 自成一卡)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantService {

    private final DataPermUserRoleGrantDao userRoleGrantDao;
    private final DataPermUserDirectGrantDao userDirectGrantDao;
    private final DataPermRoleDao roleDao;
    private final DataPermUserEffectiveSnapshotDao userEffectiveSnapshotDao;
    private final RolePermissionService rolePermissionService;
    private final DataPermReconciler reconciler;

    public MyGrantsSummaryDTO summary(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserRoleGrantSummaryRow> roleRows = userRoleGrantDao.selectActiveSummaryByUser(userId, now);
        UserDirectGrantOverviewRow directRow = userDirectGrantDao.selectActiveOverviewByUser(userId, now);

        List<MyGrantsSummaryDTO.RoleCard> cards = BeanConvertUtils.convertList(
                roleRows, MyGrantsSummaryDTO.RoleCard.class);
        for (MyGrantsSummaryDTO.RoleCard c : cards) {
            if (c.getRoleName() == null) {
                c.setRoleName(I18n.t("msg.dataperm.roleDeleted", c.getRoleId()));
            }
            if (c.getPermCount() == null) {
                c.setPermCount(0L);
            }
        }
        MyGrantsSummaryDTO.DirectOverview directOverview = directRow == null
                ? null
                : BeanConvertUtils.convert(directRow, MyGrantsSummaryDTO.DirectOverview.class);

        return MyGrantsSummaryDTO.builder()
                .roleCards(cards)
                .directOverview(directOverview)
                .stats(MyGrantsSummaryDTO.Stats.builder()
                        .roles(cards.size())
                        .direct(directOverview == null ? 0L : directOverview.getPermCount())
                        .expiringSoon(countExpiringSoon(roleRows, directRow, now))
                        .build())
                .build();
    }

    public IPage<DataPermRolePermissionItemDTO> pagePermissionsForUserRole(Long userId, Long roleId,
                                                                           int pageNum, int pageSize) {
        if (!userRoleGrantDao.existsActiveByUserAndRole(userId, roleId, LocalDateTime.now())) {
            throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, roleId);
        }
        return rolePermissionService.page(roleId, null, null, pageNum, pageSize);
    }

    public IPage<DataPermRolePermissionItemDTO> pageDirectPermissions(Long userId, int pageNum, int pageSize) {
        IPage<DataPermUserDirectGrantDetailView> page = userDirectGrantDao.pageActiveByUser(
                userId, LocalDateTime.now(), pageNum, pageSize);
        return page.convert(g -> DataPermRolePermissionItemDTO.builder()
                .scopeCode(g.getScopeCode())
                .scopeName(g.getScopeName())
                .catalogName(g.getCatalogName())
                .databaseName(g.getDatabaseName())
                .tableName(g.getTableName())
                .columnName(g.getColumnName())
                .accesses(JsonUtils.toList(g.getAccesses(), String.class))
                .effectiveTime(g.getEffectiveTime())
                .expirationTime(g.getExpirationTime())
                .build());
    }

    /** 基于 user effective snapshot 表(Ranger 端事实)的扁平审计列表。
     *  workspaceId 非空时按 workspace_member 限定。 */
    public IPage<EffectiveSnapshotRowDTO> pageEffectiveSnapshot(List<Long> userIds, List<Long> scopeCodes,
                                                                String keyword, LocalDateTime asOf,
                                                                Long workspaceId,
                                                                int pageNum, int pageSize) {
        IPage<EffectiveSnapshotRow> page = userEffectiveSnapshotDao.pageEffectiveSnapshot(
                userIds, scopeCodes, keyword, asOf, workspaceId, pageNum, pageSize);
        // source_kinds 是 JSON 数组,一行可能引用多个 role / direct;原生 SQL join 不便,service 层一次预取 map 填充 name
        Map<Long, String> roleNameById = roleDao.selectAll().stream()
                .collect(Collectors.toMap(DataPermRole::getId, DataPermRole::getName));
        return page.convert(r -> EffectiveSnapshotRowDTO.builder()
                .userId(r.getUserId())
                .username(r.getUsername())
                .version(r.getVersion())
                .snapshotTime(r.getSnapshotTime())
                .scopeCode(r.getScopeCode())
                .scopeName(r.getScopeName())
                .catalogName(r.getCatalogName())
                .databaseName(r.getDatabaseName())
                .tableName(r.getTableName())
                .columnName(r.getColumnName())
                .accesses(JsonUtils.toList(r.getAccesses(), String.class))
                .sources(parseSources(r.getSourceKinds(), roleNameById))
                .build());
    }

    private static List<EffectiveSnapshotRowDTO.Source> parseSources(String json, Map<Long, String> roleNameById) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<EffectiveSnapshotRowDTO.Source> out = JsonUtils.toList(json, EffectiveSnapshotRowDTO.Source.class);
        if (out == null) {
            return List.of();
        }
        for (EffectiveSnapshotRowDTO.Source s : out) {
            if (PermSource.Kind.ROLE.name().equals(s.getKind())) {
                s.setName(roleNameById.get(s.getId()));
            }
        }
        return out;
    }

    private long countExpiringSoon(List<UserRoleGrantSummaryRow> roleRows,
                                   UserDirectGrantOverviewRow directRow,
                                   LocalDateTime now) {
        LocalDateTime threshold = now.plusDays(7);
        long n = roleRows.stream()
                .filter(r -> r.getExpirationTime() != null
                        && !r.getExpirationTime().isBefore(now)
                        && r.getExpirationTime().isBefore(threshold))
                .count();
        if (directRow != null
                && directRow.getExpirationTime() != null
                && !directRow.getExpirationTime().isBefore(now)
                && directRow.getExpirationTime().isBefore(threshold)) {
            n++;
        }
        return n;
    }

    /**
     * 时间点审计还原:查 asOf 时刻该用户活跃 grants。
     * role grants 走 user effective snapshot(snapshot_time <= asOf 的最新 version),按 source_kinds 过滤出该 role 贡献的 perms;
     * direct grants 用其自身 effective_time / expiration_time 判断。
     */
    public List<UserGrantViewDTO> listActiveByUserAt(Long userId, LocalDateTime asOf) {
        List<UserGrantViewDTO> views = new ArrayList<>();
        boolean isHistorical = asOf.isBefore(LocalDateTime.now().minusSeconds(5));
        List<DataPermUserEffectiveSnapshot> snapshot = isHistorical
                ? userEffectiveSnapshotDao.selectAt(userId, asOf)
                : List.of();

        // role grants — 同 roleId 多条(续期场景)合并为一张 view:
        // effectiveTime 取最早,expirationTime 取最晚(任一条永久则整体永久),其余字段取最新那条。
        List<DataPermUserRoleGrantDetailView> roleGrants = userRoleGrantDao.selectActiveByUser(userId, asOf);
        Map<Long, List<DataPermUserRoleGrantDetailView>> grantsByRole = new LinkedHashMap<>();
        for (DataPermUserRoleGrantDetailView g : roleGrants) {
            grantsByRole.computeIfAbsent(g.getRoleId(), x -> new ArrayList<>()).add(g);
        }
        for (Map.Entry<Long, List<DataPermUserRoleGrantDetailView>> e : grantsByRole.entrySet()) {
            Long roleId = e.getKey();
            List<DataPermUserRoleGrantDetailView> group = e.getValue();
            String joinRoleName = group.get(0).getRoleName(); // join 出来的 role.name;role 被删时为 null
            List<DataPermRolePermissionItemDTO> perms = joinRoleName == null
                    ? Collections.emptyList()
                    : (isHistorical
                            ? snapshotPermsForRole(snapshot, roleId)
                            : rolePermissionService.listByRoleId(roleId));
            DataPermUserRoleGrantDetailView newest = group.stream()
                    .max((a, b) -> a.getEffectiveTime().compareTo(b.getEffectiveTime()))
                    .orElseThrow();
            LocalDateTime earliestEffective = group.stream()
                    .map(DataPermUserRoleGrant::getEffectiveTime)
                    .min(LocalDateTime::compareTo)
                    .orElseThrow();
            boolean hasPermanent = group.stream().anyMatch(x -> x.getExpirationTime() == null);
            LocalDateTime latestExpiration = hasPermanent ? null
                    : group.stream()
                            .map(DataPermUserRoleGrant::getExpirationTime)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
            views.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.ROLE)
                    .roleId(roleId)
                    .roleName(joinRoleName != null ? joinRoleName : I18n.t("msg.dataperm.roleDeleted", roleId))
                    .sourceApprovalId(newest.getSourceApprovalId())
                    .grantId(newest.getId())
                    .effectiveTime(earliestEffective)
                    .expirationTime(latestExpiration)
                    .endReason(newest.getEndReason())
                    .permissions(perms)
                    .build());
        }

        // direct grants
        List<DataPermUserDirectGrantDetailView> directGrants = userDirectGrantDao.selectActiveByUser(userId, asOf);
        for (DataPermUserDirectGrantDetailView g : directGrants) {
            views.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.DIRECT)
                    .roleId(null)
                    .roleName(I18n.t("msg.dataperm.directBucket"))
                    .sourceApprovalId(g.getSourceApprovalId())
                    .grantId(g.getId())
                    .effectiveTime(g.getEffectiveTime())
                    .expirationTime(g.getExpirationTime())
                    .endReason(g.getEndReason())
                    .permissions(List.of(DataPermRolePermissionItemDTO.builder()
                            .scopeCode(g.getScopeCode())
                            .scopeName(g.getScopeName())
                            .catalogName(g.getCatalogName())
                            .databaseName(g.getDatabaseName())
                            .tableName(g.getTableName())
                            .columnName(g.getColumnName())
                            .accesses(JsonUtils.toList(g.getAccesses(), String.class))
                            .build()))
                    .build());
        }
        return views;
    }

    /** 历史(EXPIRED / REVOKED / ROLE_DELETED)grants 折叠区用。 */
    public List<UserGrantViewDTO> listInactiveByUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserGrantViewDTO> views = new ArrayList<>();

        List<DataPermUserRoleGrantDetailView> roleGrants = userRoleGrantDao.selectInactiveByUser(userId, now);
        for (DataPermUserRoleGrantDetailView g : roleGrants) {
            // 失效项的内容用快照还原:每个 grant 失效瞬间对应的快照版本
            LocalDateTime asOf = g.getExpirationTime() == null ? now : g.getExpirationTime();
            List<DataPermUserEffectiveSnapshot> snap = userEffectiveSnapshotDao.selectAt(userId, asOf);
            List<DataPermRolePermissionItemDTO> perms = snapshotPermsForRole(snap, g.getRoleId());
            views.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.ROLE)
                    .roleId(g.getRoleId())
                    .roleName(g.getRoleName() != null ? g.getRoleName()
                            : I18n.t("msg.dataperm.roleDeleted", g.getRoleId()))
                    .sourceApprovalId(g.getSourceApprovalId())
                    .grantId(g.getId())
                    .effectiveTime(g.getEffectiveTime())
                    .expirationTime(g.getExpirationTime())
                    .endReason(g.getEndReason())
                    .permissions(perms)
                    .build());
        }

        List<DataPermUserDirectGrantDetailView> directGrants = userDirectGrantDao.selectInactiveByUser(userId, now);
        for (DataPermUserDirectGrantDetailView g : directGrants) {
            views.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.DIRECT)
                    .roleName(I18n.t("msg.dataperm.directBucket"))
                    .sourceApprovalId(g.getSourceApprovalId())
                    .grantId(g.getId())
                    .effectiveTime(g.getEffectiveTime())
                    .expirationTime(g.getExpirationTime())
                    .endReason(g.getEndReason())
                    .permissions(List.of(DataPermRolePermissionItemDTO.builder()
                            .scopeCode(g.getScopeCode())
                            .scopeName(g.getScopeName())
                            .catalogName(g.getCatalogName())
                            .databaseName(g.getDatabaseName())
                            .tableName(g.getTableName())
                            .columnName(g.getColumnName())
                            .accesses(JsonUtils.toList(g.getAccesses(), String.class))
                            .build()))
                    .build());
        }
        return views;
    }

    /** 撤销单条 role grant。仅 active(expiration_time IS NULL)生效,幂等。返回是否变更了 1 行。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeRoleGrant(Long grantId, Long actorUserId, String note) {
        DataPermUserRoleGrant grant = userRoleGrantDao.selectById(grantId);
        if (grant == null) {
            throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, grantId);
        }
        int rows = userRoleGrantDao.expireIfActive(
                grantId, LocalDateTime.now(), DataPermGrantEndReason.REVOKED.name(), actorUserId, note);
        if (rows == 0) {
            log.info("Role grant already expired or not active, skip revoke: id={}", grantId);
            return false;
        }
        log.info("Role grant revoked: id={}, by={}, note={}", grantId, actorUserId, note);
        reconciler.triggerNowAfterCommit("ADMIN_REVOKED");
        return true;
    }

    /** 撤销单条 direct grant。仅 active 生效,幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeDirectGrant(Long grantId, Long actorUserId, String note) {
        DataPermUserDirectGrant grant = userDirectGrantDao.selectById(grantId);
        if (grant == null) {
            throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, grantId);
        }
        int rows = userDirectGrantDao.expireIfActive(
                grantId, LocalDateTime.now(), DataPermGrantEndReason.REVOKED.name(), actorUserId, note);
        if (rows == 0) {
            log.info("Direct grant already expired or not active, skip revoke: id={}", grantId);
            return false;
        }
        log.info("Direct grant revoked: id={}, by={}, note={}", grantId, actorUserId, note);
        reconciler.triggerNowAfterCommit("ADMIN_REVOKED");
        return true;
    }

    /** 按 approval 批量撤销 —— 一次申请产生的所有 grants 一并失效。返回失效总条数。 */
    @Transactional(rollbackFor = Exception.class)
    public int revokeByApproval(Long approvalId, Long actorUserId, String note) {
        if (approvalId == null) {
            throw new BizException(DataPermErrorCode.GRANT_NOT_FOUND, "approvalId required");
        }
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (DataPermUserRoleGrant g : userRoleGrantDao.selectByApprovalId(approvalId)) {
            if (userRoleGrantDao.expireIfActive(g.getId(), now, DataPermGrantEndReason.REVOKED.name(), actorUserId,
                    note) > 0) {
                total++;
            }
        }
        for (DataPermUserDirectGrant g : userDirectGrantDao.selectByApprovalId(approvalId)) {
            if (userDirectGrantDao.expireIfActive(g.getId(), now, DataPermGrantEndReason.REVOKED.name(), actorUserId,
                    note) > 0) {
                total++;
            }
        }
        log.info("Grants revoked by approval: approvalId={}, total={}, by={}", approvalId, total, actorUserId);
        reconciler.triggerNowAfterCommit("ADMIN_REVOKED");
        return total;
    }

    /** 从用户级 snapshot 过滤出"贡献来自该 role"的 perm。snapshot 已按 asOf 取过。 */
    private static List<DataPermRolePermissionItemDTO> snapshotPermsForRole(
                                                                            List<DataPermUserEffectiveSnapshot> snapshot,
                                                                            Long roleId) {
        if (snapshot.isEmpty() || roleId == null) {
            return Collections.emptyList();
        }
        return snapshot.stream()
                .filter(r -> sourceMatches(r.getSourceKinds(), PermSource.Kind.ROLE, roleId))
                .map(GrantService::toItemDto)
                .toList();
    }

    private static DataPermRolePermissionItemDTO toItemDto(DataPermUserEffectiveSnapshot s) {
        return DataPermRolePermissionItemDTO.builder()
                .scopeCode(s.getScopeCode())
                .catalogName(s.getCatalogName())
                .databaseName(s.getDatabaseName())
                .tableName(s.getTableName())
                .columnName(s.getColumnName())
                .accesses(JsonUtils.toList(s.getAccesses(), String.class))
                .build();
    }

    private static boolean sourceMatches(String sourceKindsJson, PermSource.Kind kind, Long id) {
        if (sourceKindsJson == null || sourceKindsJson.isBlank()) {
            return false;
        }
        List<PermSource> srcs = JsonUtils.toList(sourceKindsJson, PermSource.class);
        if (srcs == null) {
            return false;
        }
        for (PermSource s : srcs) {
            if (kind == s.kind() && Objects.equals(id, s.id())) {
                return true;
            }
        }
        return false;
    }
}
