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
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.dao.DataPermBundleStatementDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserEffectiveSnapshotDao;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;
import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.entity.view.DataPermUserBundleGrantDetailView;
import io.github.zzih.rudder.dao.projection.EffectiveSnapshotRow;
import io.github.zzih.rudder.dao.projection.GrantItemRow;
import io.github.zzih.rudder.dao.projection.InactiveGrantRef;
import io.github.zzih.rudder.dao.projection.UserBundleGrantSummaryRow;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.EffectiveSnapshotRowDTO;
import io.github.zzih.rudder.service.dataperm.dto.MyGrantsSummaryDTO;
import io.github.zzih.rudder.service.dataperm.dto.ResourcePathDTO;
import io.github.zzih.rudder.service.dataperm.dto.UserGrantViewDTO;
import io.github.zzih.rudder.service.dataperm.dto.UserGrantsDTO;
import io.github.zzih.rudder.service.dataperm.reconciler.DataPermReconciler;
import io.github.zzih.rudder.service.dataperm.reconciler.PermSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户权限事实(grants) 的查询与撤销。
 *
 * <p>读展示路径统一拍平成单元组 {@link DataPermPermissionItemDTO}(当前态带分组名,历史快照带裸 access);
 * 录入态的"作用域块"结构保留在 {@link BundleStatementService}(权限包)与 direct block 表(申请)里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantService {

    private final DataPermUserBundleGrantDao userBundleGrantDao;
    private final DataPermUserDirectGrantDao directGrantDao;
    private final DataPermBundleDao bundleDao;
    private final DataPermBundleStatementDao bundleStatementDao;
    private final DataPermUserEffectiveSnapshotDao userEffectiveSnapshotDao;
    private final BundleStatementService bundleStatementService;
    private final DataPermConfigService configService;
    private final DataPermScopeAccessGroupService accessGroupService;
    private final DataPermReconciler reconciler;

    public MyGrantsSummaryDTO summary(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserBundleGrantSummaryRow> roleRows = userBundleGrantDao.selectActiveSummaryByUser(userId, now);

        List<MyGrantsSummaryDTO.RoleCard> cards = BeanConvertUtils.convertList(
                roleRows, MyGrantsSummaryDTO.RoleCard.class);
        for (MyGrantsSummaryDTO.RoleCard c : cards) {
            if (c.getBundleName() == null) {
                c.setBundleName(I18n.t("msg.dataperm.roleDeleted", c.getBundleId()));
            }
            if (c.getPermCount() == null) {
                c.setPermCount(0L);
            }
        }
        MyGrantsSummaryDTO.DirectOverview directOverview = buildDirectOverview(userId, now);

        return MyGrantsSummaryDTO.builder()
                .roleCards(cards)
                .directOverview(directOverview)
                .stats(MyGrantsSummaryDTO.Stats.builder()
                        .roles(cards.size())
                        .direct(directOverview == null ? 0L : directOverview.getPermCount())
                        .expiringSoon(countExpiringSoon(roleRows, directOverview, now))
                        .build())
                .build();
    }

    /** 当前活跃 direct 块聚合成概览:permCount = 库表行数,effective 取最早,expiration 任一永久则永久。 */
    private MyGrantsSummaryDTO.DirectOverview buildDirectOverview(Long userId, LocalDateTime now) {
        List<DataPermUserDirectGrant> blocks = directGrantDao.selectActiveByUser(userId, now);
        if (blocks.isEmpty()) {
            return null;
        }
        long permCount = directGrantDao.sumFlattenedActiveByUser(userId, now);
        GrantWindow window = mergeWindow(blocks,
                DataPermUserDirectGrant::getEffectiveTime, DataPermUserDirectGrant::getExpirationTime);
        return MyGrantsSummaryDTO.DirectOverview.builder()
                .effectiveTime(window.effective()).expirationTime(window.expiration()).permCount(permCount).build();
    }

    /** 基于 user effective snapshot 表的扁平审计列表。workspaceId 非空时按 workspace_member 限定。 */
    public IPage<EffectiveSnapshotRowDTO> pageEffectiveSnapshot(List<Long> userIds, List<Long> scopeCodes,
                                                                String keyword, LocalDateTime asOf,
                                                                Long workspaceId,
                                                                int pageNum, int pageSize) {
        IPage<EffectiveSnapshotRow> page = userEffectiveSnapshotDao.pageEffectiveSnapshot(
                userIds, scopeCodes, keyword, asOf, workspaceId, pageNum, pageSize);
        Map<Long, String> bundleNameById = bundleDao.selectAll().stream()
                .collect(Collectors.toMap(DataPermBundle::getId, DataPermBundle::getName));
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
                .sources(parseSources(r.getSourceKinds(), bundleNameById))
                .build());
    }

    private static List<EffectiveSnapshotRowDTO.Source> parseSources(String json, Map<Long, String> bundleNameById) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<EffectiveSnapshotRowDTO.Source> out = JsonUtils.toList(json, EffectiveSnapshotRowDTO.Source.class);
        if (out == null) {
            return List.of();
        }
        for (EffectiveSnapshotRowDTO.Source s : out) {
            if (PermSource.Kind.ROLE.name().equals(s.getKind())) {
                s.setName(bundleNameById.get(s.getId()));
            }
        }
        return out;
    }

    private long countExpiringSoon(List<UserBundleGrantSummaryRow> roleRows,
                                   MyGrantsSummaryDTO.DirectOverview directOverview,
                                   LocalDateTime now) {
        LocalDateTime threshold = now.plusDays(7);
        long n = roleRows.stream()
                .filter(r -> r.getExpirationTime() != null
                        && !r.getExpirationTime().isBefore(now)
                        && r.getExpirationTime().isBefore(threshold))
                .count();
        if (directOverview != null
                && directOverview.getExpirationTime() != null
                && !directOverview.getExpirationTime().isBefore(now)
                && directOverview.getExpirationTime().isBefore(threshold)) {
            n++;
        }
        return n;
    }

    /**
     * 时间点审计还原:asOf 时刻该用户活跃 grants。
     * role grants 内容历史走 snapshot、当前走 role 块;direct grants 当前走 direct 块,展示均拍平成单元组。
     */
    public List<UserGrantViewDTO> listActiveByUserAt(Long userId, LocalDateTime asOf) {
        List<UserGrantViewDTO> views = new ArrayList<>();
        boolean isHistorical = asOf.isBefore(LocalDateTime.now().minusSeconds(5));
        List<DataPermUserEffectiveSnapshot> snapshot = isHistorical
                ? userEffectiveSnapshotDao.selectAt(userId, asOf)
                : List.of();

        List<DataPermUserBundleGrantDetailView> bundleGrants = userBundleGrantDao.selectActiveByUser(userId, asOf);
        Map<Long, List<DataPermUserBundleGrantDetailView>> grantsByRole = new LinkedHashMap<>();
        for (DataPermUserBundleGrantDetailView g : bundleGrants) {
            grantsByRole.computeIfAbsent(g.getBundleId(), x -> new ArrayList<>()).add(g);
        }
        Map<Long, String> groupNameById = isHistorical ? Map.of() : accessGroupService.allNames();
        for (Map.Entry<Long, List<DataPermUserBundleGrantDetailView>> e : grantsByRole.entrySet()) {
            Long bundleId = e.getKey();
            List<DataPermUserBundleGrantDetailView> group = e.getValue();
            String joinBundleName = group.get(0).getBundleName();
            List<DataPermPermissionItemDTO> perms;
            if (joinBundleName == null) {
                perms = Collections.emptyList();
            } else if (isHistorical) {
                perms = snapshotPermsForRole(snapshot, bundleId);
            } else {
                perms = new ArrayList<>();
                for (DataPermStatementDTO b : bundleStatementService.listStatements(bundleId, groupNameById)) {
                    perms.addAll(expandStatementToItems(b));
                }
            }
            DataPermUserBundleGrantDetailView newest = group.stream()
                    .max((a, b) -> a.getEffectiveTime().compareTo(b.getEffectiveTime()))
                    .orElseThrow();
            GrantWindow window = mergeWindow(group,
                    DataPermUserBundleGrant::getEffectiveTime, DataPermUserBundleGrant::getExpirationTime);
            views.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.ROLE)
                    .bundleId(bundleId)
                    .bundleName(joinBundleName != null ? joinBundleName : I18n.t("msg.dataperm.roleDeleted", bundleId))
                    .sourceApprovalId(newest.getSourceApprovalId())
                    .grantId(newest.getId())
                    .effectiveTime(window.effective())
                    .expirationTime(window.expiration())
                    .endReason(newest.getEndReason())
                    .permissions(perms)
                    .build());
        }

        for (DataPermStatementDTO block : activeDirectGrants(userId, asOf, isHistorical ? null : groupNameById)) {
            views.add(directGrantView(block));
        }
        return views;
    }

    /**
     * 管理端「按用户」聚合视图:分页列出有 active 授权的用户,每个带其当前权限包 + 直接授权来源摘要(仅计数,明细经 pageGrantItems 懒加载)。
     * 按用户分页(total = 活跃用户数),仅装配当前页用户,避免全量。username 由调用方按 userId 补。
     */
    public IPage<UserGrantsDTO> pageActiveGrantsByUser(Collection<Long> restrictUserIds, LocalDateTime asOf,
                                                       int pageNum, int pageSize) {
        int page = Math.max(pageNum, 1);
        int size = Math.max(pageSize, 1);
        Set<Long> userIds = new TreeSet<>(userBundleGrantDao.selectDistinctActiveUserIds(asOf));
        userIds.addAll(directGrantDao.selectDistinctActiveUserIds(asOf));
        if (restrictUserIds != null) {
            userIds.retainAll(restrictUserIds); // 非 SUPER_ADMIN:限定到当前 workspace 成员
        }
        List<Long> ordered = new ArrayList<>(userIds);
        int from = Math.min((page - 1) * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        List<UserGrantsDTO> records = ordered.subList(from, to).stream()
                .map(uid -> new UserGrantsDTO(uid, grantSummariesByUser(uid, asOf)))
                .toList();
        return new Page<UserGrantsDTO>(page, size, ordered.size()).setRecords(records);
    }

    /**
     * 聚合视图每个用户的授权来源摘要:权限包逐个(permCount = 包内库表行数)+ 直接授权合并一条(permCount = 活跃库表行数)。
     * 仅计数不展开,权限项明细经 {@link #pageGrantItems} 分页懒加载。
     */
    private List<UserGrantViewDTO> grantSummariesByUser(Long userId, LocalDateTime asOf) {
        List<UserGrantViewDTO> out = new ArrayList<>();
        for (UserBundleGrantSummaryRow row : userBundleGrantDao.selectActiveSummaryByUser(userId, asOf)) {
            out.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.ROLE)
                    .bundleId(row.getBundleId())
                    .bundleName(row.getBundleName() != null ? row.getBundleName()
                            : I18n.t("msg.dataperm.roleDeleted", row.getBundleId()))
                    .grantId(row.getGrantId())
                    .sourceApprovalId(row.getSourceApprovalId())
                    .effectiveTime(row.getEffectiveTime())
                    .expirationTime(row.getExpirationTime())
                    .permCount(row.getPermCount() == null ? 0L : row.getPermCount())
                    .build());
        }
        MyGrantsSummaryDTO.DirectOverview direct = buildDirectOverview(userId, asOf);
        if (direct != null) {
            out.add(UserGrantViewDTO.builder()
                    .kind(UserGrantViewDTO.Kind.DIRECT)
                    .bundleName(I18n.t("msg.dataperm.directBucket"))
                    .effectiveTime(direct.getEffectiveTime())
                    .expirationTime(direct.getExpirationTime())
                    .permCount(direct.getPermCount())
                    .build());
        }
        return out;
    }

    /**
     * 展开某权限包 / 直接授权时,在 SQL 里用 JSON_TABLE 把资源行各层数组笛卡尔展开成拍平单元组并原生分页(LIMIT/OFFSET),
     * total 为展开后的单元组条数(各层数组长度乘积之和,与卡片 permCount 同口径)。
     * {@code bundleId} 非空 = 该用户某权限包的库表行(校验用户当前持有);为空 = 该用户全部活跃直接授权库表行。
     * 「我的数据权限」与「数据权限总览」共用:前者 userId = 当前登录用户,后者为目标用户。
     */
    public IPage<DataPermPermissionItemDTO> pageGrantItems(Long userId, Long bundleId, int pageNum, int pageSize) {
        Map<Long, String> groupNameById = accessGroupService.allNames();
        LocalDateTime now = LocalDateTime.now();
        if (bundleId != null) {
            // 权限包行的生效 / 到期取该用户对此包的授权窗口(多 grant 合并:最早生效,任一永久则永久,否则最晚到期)
            List<DataPermUserBundleGrantDetailView> grants = userBundleGrantDao.selectActiveByUser(userId, now)
                    .stream().filter(g -> bundleId.equals(g.getBundleId())).toList();
            if (grants.isEmpty()) {
                throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, bundleId);
            }
            GrantWindow window = mergeWindow(grants,
                    DataPermUserBundleGrant::getEffectiveTime, DataPermUserBundleGrant::getExpirationTime);
            return bundleStatementDao.pageFlattenedByBundle(bundleId, pageNum, pageSize)
                    .convert(r -> toItem(r, groupNameById, window.effective(), window.expiration()));
        }
        return directGrantDao.pageActiveFlattenedByUser(userId, now, pageNum, pageSize)
                .convert(r -> toItem(r, groupNameById, r.getEffectiveTime(), r.getExpirationTime()));
    }

    /** 合并多条 grant 的有效期窗口:最早 effective +(任一永久则永久,否则最晚 expiration)。 */
    private static <T> GrantWindow mergeWindow(List<T> grants, Function<T, LocalDateTime> effectiveOf,
                                               Function<T, LocalDateTime> expirationOf) {
        LocalDateTime effective = grants.stream().map(effectiveOf).min(LocalDateTime::compareTo).orElse(null);
        boolean permanent = grants.stream().anyMatch(g -> expirationOf.apply(g) == null);
        LocalDateTime expiration = permanent ? null
                : grants.stream().map(expirationOf).max(LocalDateTime::compareTo).orElse(null);
        return new GrantWindow(effective, expiration);
    }

    private record GrantWindow(LocalDateTime effective, LocalDateTime expiration) {
    }

    private DataPermPermissionItemDTO toItem(GrantItemRow r, Map<Long, String> groupNameById,
                                             LocalDateTime eff, LocalDateTime exp) {
        return DataPermPermissionItemDTO.builder()
                .scopeCode(r.getScopeCode()).scopeName(r.getScopeName())
                .catalogName(r.getCatalogName()).databaseName(r.getDatabaseName())
                .tableName(r.getTableName()).columnName(r.getColumnName())
                .groupNames(DataPermScopeAccessGroupService.resolveNames(
                        DataPermStatementSupport.parseGroupIds(r.getGroupIds()), groupNameById))
                .effectiveTime(eff).expirationTime(exp)
                .build();
    }

    /**
     * 历史(EXPIRED / REVOKED / ROLE_DELETED)grants 折叠区用,跨 role / direct 两表按失效时间统一分页。
     * 先用 UNION 取一页 (id, kind),再按 kind 回各自表补全明细并按页内顺序重排。
     */
    public IPage<UserGrantViewDTO> pageInactiveByUser(Long userId, int pageNum, int pageSize) {
        LocalDateTime now = LocalDateTime.now();
        IPage<InactiveGrantRef> refs = userBundleGrantDao.selectInactiveRefsPage(userId, now, pageNum, pageSize);

        List<Long> roleIds = refs.getRecords().stream()
                .filter(r -> "ROLE".equals(r.getKind())).map(InactiveGrantRef::getId).toList();
        List<Long> directIds = refs.getRecords().stream()
                .filter(r -> "DIRECT".equals(r.getKind())).map(InactiveGrantRef::getId).toList();

        Map<Long, UserGrantViewDTO> roleViews = new HashMap<>();
        if (!roleIds.isEmpty()) {
            Map<LocalDateTime, List<DataPermUserEffectiveSnapshot>> snapByAsOf = new HashMap<>();
            for (DataPermUserBundleGrantDetailView g : userBundleGrantDao.selectByIds(roleIds)) {
                LocalDateTime asOf = g.getExpirationTime() == null ? now : g.getExpirationTime();
                List<DataPermUserEffectiveSnapshot> snap =
                        snapByAsOf.computeIfAbsent(asOf, k -> userEffectiveSnapshotDao.selectAt(userId, k));
                List<DataPermPermissionItemDTO> perms = snapshotPermsForRole(snap, g.getBundleId());
                roleViews.put(g.getId(), UserGrantViewDTO.builder()
                        .kind(UserGrantViewDTO.Kind.ROLE)
                        .bundleId(g.getBundleId())
                        .bundleName(g.getBundleName() != null ? g.getBundleName()
                                : I18n.t("msg.dataperm.roleDeleted", g.getBundleId()))
                        .sourceApprovalId(g.getSourceApprovalId())
                        .grantId(g.getId())
                        .effectiveTime(g.getEffectiveTime())
                        .expirationTime(g.getExpirationTime())
                        .endReason(g.getEndReason())
                        .permissions(perms)
                        .build());
            }
        }

        Map<Long, UserGrantViewDTO> directViews = new HashMap<>();
        if (!directIds.isEmpty()) {
            List<DataPermUserDirectGrant> directs = directGrantDao.selectByIds(directIds);
            Map<Long, String> nameById = accessGroupService.allNames();
            Map<Long, List<DataPermUserDirectGrantResource>> resourcesByBlock = resourcesByBlock(directs);
            Map<Long, String> scopeNameByCode = scopeNameByCode();
            for (DataPermUserDirectGrant b : directs) {
                directViews.put(b.getId(), directGrantView(toBlockDto(b, nameById, resourcesByBlock, scopeNameByCode)));
            }
        }

        List<UserGrantViewDTO> ordered = new ArrayList<>(refs.getRecords().size());
        for (InactiveGrantRef ref : refs.getRecords()) {
            UserGrantViewDTO v =
                    "ROLE".equals(ref.getKind()) ? roleViews.get(ref.getId()) : directViews.get(ref.getId());
            if (v != null) {
                ordered.add(v);
            }
        }
        return new Page<UserGrantViewDTO>(refs.getCurrent(), refs.getSize(), refs.getTotal()).setRecords(ordered);
    }

    /** 撤销单条 role grant。仅 active(expiration_time IS NULL)生效,幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeBundleGrant(Long grantId, Long actorUserId, String note) {
        DataPermUserBundleGrant grant = userBundleGrantDao.selectById(grantId);
        if (grant == null) {
            throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, grantId);
        }
        int rows = userBundleGrantDao.expireIfActive(
                grantId, LocalDateTime.now(), DataPermGrantEndReason.REVOKED.name(), actorUserId, note);
        if (rows == 0) {
            log.info("Role grant already expired or not active, skip revoke: id={}", grantId);
            return false;
        }
        log.info("Role grant revoked: id={}, by={}, note={}", grantId, actorUserId, note);
        reconciler.triggerNowAfterCommit("ADMIN_REVOKED");
        return true;
    }

    /** 撤销单个 direct 授权块。仅 active 生效,幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeDirectGrant(Long statementId, Long actorUserId, String note) {
        DataPermUserDirectGrant block = directGrantDao.selectById(statementId);
        if (block == null) {
            throw new NotFoundException(DataPermErrorCode.GRANT_NOT_FOUND, statementId);
        }
        int rows = directGrantDao.expireIfActive(
                statementId, LocalDateTime.now(), DataPermGrantEndReason.REVOKED.name(), actorUserId, note);
        if (rows == 0) {
            log.info("Direct block already expired or not active, skip revoke: id={}", statementId);
            return false;
        }
        log.info("Direct block revoked: id={}, by={}, note={}", statementId, actorUserId, note);
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
        for (DataPermUserBundleGrant g : userBundleGrantDao.selectByApprovalId(approvalId)) {
            if (userBundleGrantDao.expireIfActive(g.getId(), now, DataPermGrantEndReason.REVOKED.name(), actorUserId,
                    note) > 0) {
                total++;
            }
        }
        for (DataPermUserDirectGrant b : directGrantDao.selectByApprovalId(approvalId)) {
            if (directGrantDao.expireIfActive(b.getId(), now, DataPermGrantEndReason.REVOKED.name(), actorUserId,
                    note) > 0) {
                total++;
            }
        }
        log.info("Grants revoked by approval: approvalId={}, total={}, by={}", approvalId, total, actorUserId);
        reconciler.triggerNowAfterCommit("ADMIN_REVOKED");
        return total;
    }

    // ---------- 内部:direct 块装配 / 拍平 ----------

    /**
     * 加载 user 在 asOf 活跃的 direct 块(含库表行 + 分组名 + scopeName),组装成 DTO。
     * suppliedNames 非空时复用调用方已加载的分组名表,避免再次全表扫描。
     */
    private List<DataPermStatementDTO> activeDirectGrants(Long userId, LocalDateTime asOf,
                                                          Map<Long, String> suppliedNames) {
        List<DataPermUserDirectGrant> blocks = directGrantDao.selectActiveByUser(userId, asOf);
        if (blocks.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = suppliedNames != null ? suppliedNames : accessGroupService.allNames();
        Map<Long, List<DataPermUserDirectGrantResource>> resourcesByBlock = resourcesByBlock(blocks);
        Map<Long, String> scopeNameByCode = scopeNameByCode();
        return blocks.stream().map(b -> toBlockDto(b, nameById, resourcesByBlock, scopeNameByCode)).toList();
    }

    /** scopeCode → 作用域名,一次取全量(作用域是闭集小表),replace 逐块 findScope 的 N+1。 */
    private Map<Long, String> scopeNameByCode() {
        Map<Long, String> out = new HashMap<>();
        for (DataPermScopeDTO s : configService.listScopes()) {
            out.put(s.getCode(), s.getName());
        }
        return out;
    }

    /** 批量拉这些块的库表行并按 statementId 归组,避免逐块查询(N+1)。 */
    private Map<Long, List<DataPermUserDirectGrantResource>> resourcesByBlock(List<DataPermUserDirectGrant> blocks) {
        List<Long> statementIds = blocks.stream().map(DataPermUserDirectGrant::getId).toList();
        Map<Long, List<DataPermUserDirectGrantResource>> out = new LinkedHashMap<>();
        for (DataPermUserDirectGrantResource r : directGrantDao.selectResourcesByStatementIds(statementIds)) {
            out.computeIfAbsent(r.getStatementId(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    private DataPermStatementDTO toBlockDto(DataPermUserDirectGrant b, Map<Long, String> nameById,
                                            Map<Long, List<DataPermUserDirectGrantResource>> resourcesByBlock,
                                            Map<Long, String> scopeNameByCode) {
        List<DataPermUserDirectGrantResource> resources = resourcesByBlock.getOrDefault(b.getId(), List.of());
        List<Long> groupIds = DataPermStatementSupport.parseGroupIds(b.getGroupIds());
        DataPermStatementDTO dto = new DataPermStatementDTO();
        dto.setId(b.getId());
        dto.setScopeCode(b.getScopeCode());
        dto.setScopeName(scopeNameByCode.get(b.getScopeCode()));
        dto.setGroupIds(groupIds);
        dto.setGroupNames(DataPermScopeAccessGroupService.resolveNames(groupIds, nameById));
        dto.setResources(resources.stream()
                .map(r -> BundleStatementService.toResourcePath(r.getCatalogNames(), r.getDatabaseNames(),
                        r.getTableNames(), r.getColumnNames()))
                .toList());
        dto.setEffectiveTime(b.getEffectiveTime());
        dto.setExpirationTime(b.getExpirationTime());
        dto.setEndReason(b.getEndReason());
        dto.setSourceApprovalId(b.getSourceApprovalId());
        return dto;
    }

    private UserGrantViewDTO directGrantView(DataPermStatementDTO block) {
        return UserGrantViewDTO.builder()
                .kind(UserGrantViewDTO.Kind.DIRECT)
                .bundleName(I18n.t("msg.dataperm.directBucket"))
                .grantId(block.getId())
                .sourceApprovalId(block.getSourceApprovalId())
                .effectiveTime(block.getEffectiveTime())
                .expirationTime(block.getExpirationTime())
                .endReason(block.getEndReason())
                .permissions(expandStatementToItems(block))
                .build();
    }

    /** 一个作用域块 → 拍平成多条单元组(各层数组笛卡尔积,空层 → null);带分组名供展示。 */
    private static List<DataPermPermissionItemDTO> expandStatementToItems(DataPermStatementDTO b) {
        List<DataPermPermissionItemDTO> out = new ArrayList<>();
        if (b.getResources() == null) {
            return out;
        }
        for (ResourcePathDTO r : b.getResources()) {
            for (String[] t : DataPermStatementSupport.cartesian(orNull(r.getCatalogNames()),
                    orNull(r.getDatabaseNames()), orNull(r.getTableNames()), orNull(r.getColumnNames()))) {
                out.add(DataPermPermissionItemDTO.builder()
                        .scopeCode(b.getScopeCode())
                        .scopeName(b.getScopeName())
                        .catalogName(t[0]).databaseName(t[1]).tableName(t[2]).columnName(t[3])
                        .groupNames(b.getGroupNames())
                        .effectiveTime(b.getEffectiveTime())
                        .expirationTime(b.getExpirationTime())
                        .build());
            }
        }
        return out;
    }

    private static List<String> orNull(List<String> v) {
        return v == null || v.isEmpty() ? Collections.singletonList(null) : v;
    }

    /** 从用户级 snapshot 过滤出"贡献来自该 role"的 perm(单元组 + 裸 access)。 */
    private static List<DataPermPermissionItemDTO> snapshotPermsForRole(
                                                                        List<DataPermUserEffectiveSnapshot> snapshot,
                                                                        Long bundleId) {
        if (snapshot.isEmpty() || bundleId == null) {
            return Collections.emptyList();
        }
        return snapshot.stream()
                .filter(r -> sourceMatches(r.getSourceKinds(), PermSource.Kind.ROLE, bundleId))
                .map(GrantService::toItemDto)
                .toList();
    }

    private static DataPermPermissionItemDTO toItemDto(DataPermUserEffectiveSnapshot s) {
        return DataPermPermissionItemDTO.builder()
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
