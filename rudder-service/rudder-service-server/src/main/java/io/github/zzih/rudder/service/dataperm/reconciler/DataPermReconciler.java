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

package io.github.zzih.rudder.service.dataperm.reconciler;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermBundleStatementDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserEffectiveSnapshotDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.entity.view.DataPermBundleStatementResourceView;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantResourceView;
import io.github.zzih.rudder.service.coordination.TransactionAfterCommit;
import io.github.zzih.rudder.service.coordination.scheduling.ClusterScheduledTask;
import io.github.zzih.rudder.service.coordination.scheduling.ClusterScheduler;
import io.github.zzih.rudder.service.dataperm.adapter.RangerAdapterRegistry;
import io.github.zzih.rudder.service.dataperm.adapter.RangerResourceAdapter;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerAdminRestClient;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicy;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyAccess;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyItem;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyResource;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.service.DataPermScopeAccessGroupService;
import io.github.zzih.rudder.service.dataperm.service.DataPermStatementSupport;
import io.github.zzih.rudder.service.notification.NotificationService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把 Rudder DB(desired)对账到 Ranger(actual)。全集群单 leader 由 ClusterScheduler Redis 锁保证。
 * 整轮失败连续到阈值触发 DATA_PERM_RECONCILE_RANGER_DOWN 告警;部分失败下轮自愈不告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermReconciler {

    public static final String SCHEDULER_KEY = "rudder:dataperm:reconcile";

    private final DataPermConfigService configService;
    private final ClusterScheduler clusterScheduler;
    private final RangerAdminRestClient rangerClient;
    private final RangerAdapterRegistry adapterRegistry;
    private final NotificationService notificationService;

    private final DataPermUserBundleGrantDao bundleGrantDao;
    private final DataPermUserDirectGrantDao directGrantDao;
    private final DataPermBundleStatementDao bundleStatementDao;
    private final DataPermScopeAccessGroupService accessGroupService;
    private final DataPermUserEffectiveSnapshotDao userEffectiveSnapshotDao;
    private final UserDao userDao;

    /** 连续整轮失败计数。整轮 OK 时清零。 */
    private final AtomicInteger consecutiveFullFailureCount = new AtomicInteger(0);
    /** 防止同阈值反复告警:一旦告警过,直到下轮成功才允许再次告警。 */
    private volatile boolean alertedRangerDown = false;
    /** 并发写线程池,首轮启动时按当时配置创建;reconfigure 时若 size 变化会替换。 */
    private volatile ExecutorService writeExecutor;

    /** 当前 writeExecutor 池大小,用于判断 reconfigure 时是否需要替换。 */
    private int currentConcurrency = -1;

    /** 用户 canonical desired key 内存缓存,稳态轮次直接命中跳过 DB sameSnapshot 比对。重启清空,首轮回退 DB 自愈。 */
    private final ConcurrentMap<Long, String> lastCanonicalKey = new ConcurrentHashMap<>();

    /** 不论 enabled 与否先注册;reconcileOnce 入口读 config 自决,支持 hot reload。 */
    @PostConstruct
    public void registerScheduler() {
        reconfigureScheduler();
    }

    /** 按当前 config 重设 interval / lockTtl / writeConcurrency。同 key 重复 schedule 替换 task,heartbeat / lock 不受影响。 */
    public void reconfigureScheduler() {
        DataPermConfigDTO config = configService.active();
        Duration interval = Duration.ofSeconds(Math.max(60, config.getReconcileIntervalSeconds()));
        Duration lockTtl = Duration.ofSeconds(Math.max(
                config.getReconcileLockTtlSeconds(), interval.toSeconds() + 60));
        clusterScheduler.schedule(new ClusterScheduledTask(SCHEDULER_KEY, interval, lockTtl, this::reconcileOnce));
        ensureWriteExecutor(config.getRangerWriteConcurrency());
        log.info("DataPermReconciler scheduled: interval={}s, lockTtl={}s", interval.toSeconds(), lockTtl.toSeconds());
    }

    public void triggerNow(String reason) {
        clusterScheduler.triggerNow(SCHEDULER_KEY, reason);
    }

    /** 事务内调用入口:推迟到 commit 后触发,避免 reconcile 读不到本事务未提交的 grant 行(READ_COMMITTED)。 */
    public void triggerNowAfterCommit(String reason) {
        TransactionAfterCommit.run(() -> triggerNow(reason));
    }

    /** 异常吞掉,告警走 evaluateRound 基于 stats 判定,避免单轮抛出搅乱 scheduler。 */
    public void reconcileOnce() {
        DataPermConfigDTO config = configService.active();
        if (!Boolean.TRUE.equals(config.getEnabled()) || (!Boolean.TRUE.equals(config.getRangerModeEnabled())
                && !Boolean.TRUE.equals(config.getLocalModeEnabled()))) {
            log.debug("DataPerm disabled or both modes off, skip reconcile round");
            return;
        }
        long t0 = System.currentTimeMillis();
        ReconcileStats stats;
        try {
            stats = doReconcile(config);
        } catch (Exception e) {
            log.error("DataPermReconciler round crashed unexpectedly", e);
            stats = new ReconcileStats(0, 0, 0, 0, 0, 0, false, e.getMessage());
        }
        evaluateRound(stats, config);
        log.info("DataPerm reconcile round done: {} elapsed={}ms", stats, System.currentTimeMillis() - t0);
    }

    private ReconcileStats doReconcile(DataPermConfigDTO config) {
        LocalDateTime now = LocalDateTime.now();

        Set<Long> activeGrantUserIds = new TreeSet<>();
        activeGrantUserIds.addAll(bundleGrantDao.selectDistinctActiveUserIds(now));
        activeGrantUserIds.addAll(directGrantDao.selectDistinctActiveUserIds(now));

        // 撤光全部 grant 的 user 也要纳入本轮:snapshot 表里仍有非 sentinel 版本时必须写 sentinel 让 Local 鉴权立即生效。
        Set<Long> reconcileUserIds = new TreeSet<>(activeGrantUserIds);
        reconcileUserIds.addAll(userEffectiveSnapshotDao.selectUserIdsWithActiveSnapshot());

        if (reconcileUserIds.isEmpty()) {
            log.debug("No users to reconcile, no-op");
            return new ReconcileStats(0, 0, 0, 0, 0, 0, true, null);
        }

        Map<Long, User> userById = new HashMap<>();
        for (User u : userDao.selectByIds(reconcileUserIds)) {
            userById.put(u.getId(), u);
        }
        if (userById.isEmpty()) {
            return new ReconcileStats(0, 0, 0, 0, 0, 0, true, null);
        }

        Map<Long, List<DataPermUserBundleGrant>> bundleGrantsByUser = new HashMap<>();
        Set<Long> referencedBundleIds = new HashSet<>();
        for (DataPermUserBundleGrant g : bundleGrantDao.selectActiveByUserIds(userById.keySet(), now)) {
            bundleGrantsByUser.computeIfAbsent(g.getUserId(), k -> new ArrayList<>()).add(g);
            referencedBundleIds.add(g.getBundleId());
        }
        Map<Long, List<DataPermBundleStatementResourceView>> bundleStatementCache = new HashMap<>();
        for (DataPermBundleStatementResourceView v : bundleStatementDao
                .selectResourceViewsByBundleIds(referencedBundleIds)) {
            bundleStatementCache.computeIfAbsent(v.getBundleId(), k -> new ArrayList<>()).add(v);
        }
        Map<Long, List<DataPermUserDirectGrantResourceView>> directGrantsByUser = new HashMap<>();
        for (DataPermUserDirectGrantResourceView v : directGrantDao
                .selectActiveResourceViewsByUserIds(userById.keySet(), now)) {
            directGrantsByUser.computeIfAbsent(v.getUserId(), k -> new ArrayList<>()).add(v);
        }

        // 操作分组展开:grant 存 group id,desired 物化成裸 access。分组总数小,单轮一次性加载。
        Map<Long, List<String>> accessesByGroupId = accessGroupService.allAccessesByGroupId();

        // role 块展开按 bundleId 预算一次,跨持有同一 role 的所有用户复用。
        Map<Long, RoleExpansion> roleExpansions = new HashMap<>();
        bundleStatementCache
                .forEach((bundleId, views) -> roleExpansions.put(bundleId, expandRole(views, accessesByGroupId)));

        Map<Long, List<DesiredPolicy>> desiredByUser = new HashMap<>();
        for (User user : userById.values()) {
            List<DesiredPolicy> desired;
            try {
                desired = computeDesired(user,
                        bundleGrantsByUser.getOrDefault(user.getId(), List.of()),
                        directGrantsByUser.getOrDefault(user.getId(), List.of()),
                        roleExpansions,
                        accessesByGroupId);
            } catch (Exception e) {
                log.error("computeDesired failed for user={}: {}", user.getId(), e.toString(), e);
                desired = List.of();
            }
            desiredByUser.put(user.getId(), desired);
        }

        // snapshot 写入跟 Ranger apply 解耦:Local 鉴权直接消费 snapshot,撤权立即生效不等 Ranger。
        for (Map.Entry<Long, List<DesiredPolicy>> e : desiredByUser.entrySet()) {
            try {
                writeSnapshotIfChanged(e.getKey(), e.getValue(), now);
            } catch (Exception ex) {
                log.warn("Write user effective snapshot failed: user={}, err={}", e.getKey(), ex.toString());
            }
        }

        // 退出 active 集合的 user(grant 全过期/被删)从 cache 清掉,避免长跑实例无界增长。
        lastCanonicalKey.keySet().retainAll(reconcileUserIds);

        // 仅 rangerModeEnabled 推 Ranger;Local-only 部署到此结束。
        if (!Boolean.TRUE.equals(config.getRangerModeEnabled())) {
            return new ReconcileStats(userById.size(), userById.size(), 0, 0, 0, 0, true, null);
        }

        // 扫所有 enabled scope:无 desired 的 service 也要拉 actual,否则 orphan 永远撤销不掉。
        // rangerServiceName 是 Ranger 端 service 标识,Ranger mode 开时校验过非空。
        Set<String> rangerServiceNames = new HashSet<>();
        for (DataPermScopeDTO s : configService.listScopes()) {
            if (Boolean.TRUE.equals(s.getEnabled()) && s.getRangerServiceName() != null
                    && !s.getRangerServiceName().isBlank()) {
                rangerServiceNames.add(s.getRangerServiceName());
            }
        }

        // SERVICE_NOT_FOUND 不能当 empty actual,否则下游对不存在的 service 反复 CREATE 触发假冲突。
        // 保留全量(含非 rudder-)给 takeover 走内存找 resource 占位者用,filter 下沉到 diff/takeover 各自处理。
        Map<String, Map<String, RangerPolicy>> actualByService = new HashMap<>();
        Set<String> brokenServices = new HashSet<>();
        boolean rangerReachable = true;
        String lastError = null;
        for (String svc : rangerServiceNames) {
            try {
                List<RangerPolicy> policies = rangerClient.listPoliciesInService(svc);
                Map<String, RangerPolicy> byName = new HashMap<>();
                for (RangerPolicy p : policies) {
                    byName.put(p.getName(), p);
                }
                actualByService.put(svc, byName);
            } catch (BizException e) {
                lastError = e.getMessage();
                if (e.getErrorCode() == DataPermErrorCode.RANGER_UNREACHABLE) {
                    rangerReachable = false;
                    log.error("Ranger Admin unreachable while listing service={}", svc, e);
                    break;
                }
                if (e.getErrorCode() == DataPermErrorCode.RANGER_SERVICE_NOT_FOUND) {
                    log.warn("Ranger service not found, skip until admin creates it: {}", svc);
                    brokenServices.add(svc);
                    continue;
                }
                log.error("List policies failed for service={}, skip this service", svc, e);
                actualByService.put(svc, Collections.emptyMap());
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("List policies unexpected failure for service={}", svc, e);
                actualByService.put(svc, Collections.emptyMap());
            }
        }
        if (!rangerReachable) {
            return new ReconcileStats(userById.size(), 0, userById.size(), 0, 0, 0, false, lastError);
        }

        // Ranger phase 内部过滤掉 brokenService 上的 desired,避免对 SERVICE_NOT_FOUND 反复 CREATE 假冲突。
        Map<Long, List<DesiredPolicy>> desiredForRanger = new HashMap<>(desiredByUser.size());
        if (brokenServices.isEmpty()) {
            desiredForRanger.putAll(desiredByUser);
        } else {
            desiredByUser.forEach((uid, list) -> desiredForRanger.put(uid, list.stream()
                    .filter(d -> !brokenServices.contains(d.rangerServiceName()))
                    .toList()));
        }

        for (User user : userById.values()) {
            if (!desiredForRanger.getOrDefault(user.getId(), List.of()).isEmpty()) {
                try {
                    rangerClient.ensureUser(user.getUsername());
                } catch (Exception e) {
                    log.debug("Ranger ensureUser threw for user={}", user.getUsername(), e);
                }
            }
        }

        Map<PolicyKey, MergedPolicy> mergedByKey = mergeAcrossUsers(desiredForRanger);
        List<Change> changes = diff(mergedByKey, actualByService);

        ExecutorService executor = ensureWriteExecutor(config.getRangerWriteConcurrency());
        ConcurrentLinkedQueue<FailedChange> failed = new ConcurrentLinkedQueue<>();
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger updated = new AtomicInteger(0);
        AtomicInteger deleted = new AtomicInteger(0);
        // Ranger 资源唯一,旧 policy 未删完就 CREATE 会撞 "Another policy already exists"。
        applyPhase(changes.stream().filter(c -> c.kind() == Change.Kind.DELETE).toList(),
                executor, failed, created, updated, deleted, actualByService);
        applyPhase(changes.stream().filter(c -> c.kind() != Change.Kind.DELETE).toList(),
                executor, failed, created, updated, deleted, actualByService);

        Set<Long> failedUserIds = new HashSet<>();
        for (FailedChange fc : failed) {
            failedUserIds.addAll(fc.change().userIds());
            log.warn("Apply change failed: users={}, op={}, policy={}, error={}",
                    fc.change().userIds(), fc.change().kind(),
                    fc.change().policyName(), fc.error().toString());
        }
        int successUsers = userById.size() - failedUserIds.size();

        return new ReconcileStats(
                userById.size(), successUsers, failedUserIds.size(),
                created.get(), updated.get(), deleted.get(),
                true, failed.isEmpty() ? null : failed.peek().error().toString());
    }

    // ---------- desired ----------

    private List<DesiredPolicy> computeDesired(User user,
                                               List<DataPermUserBundleGrant> bundleGrants,
                                               List<DataPermUserDirectGrantResourceView> directViews,
                                               Map<Long, RoleExpansion> roleExpansions,
                                               Map<Long, List<String>> accessesByGroupId) {
        Long userId = user.getId();
        Map<ItemKey, Set<String>> accessesByKey = new LinkedHashMap<>();
        Map<ItemKey, LinkedHashSet<PermSource>> sourcesByKey = new LinkedHashMap<>();
        // scope 元数据从 grant 视图的 JOIN 列收集;同一 scopeCode 跨多行字段一致,首次见到即填。
        Map<Long, ScopeRef> scopeByCode = new HashMap<>();

        // role 块展开与 user 无关,复用本轮按 bundleId 预算好的结果(避免 users × rows 次重复 JSON 解析)。
        for (DataPermUserBundleGrant g : bundleGrants) {
            RoleExpansion exp = roleExpansions.get(g.getBundleId());
            if (exp == null) {
                continue;
            }
            PermSource src = PermSource.role(g.getBundleId());
            for (Map.Entry<ItemKey, Set<String>> en : exp.keyAccesses().entrySet()) {
                accessesByKey.computeIfAbsent(en.getKey(), x -> new HashSet<>()).addAll(en.getValue());
                sourcesByKey.computeIfAbsent(en.getKey(), x -> new LinkedHashSet<>()).add(src);
            }
            exp.scopeByCode().forEach(scopeByCode::putIfAbsent);
        }

        for (DataPermUserDirectGrantResourceView v : directViews) {
            PermSource src = PermSource.direct(v.getStatementId());
            Set<String> accesses = resolveGroupAccesses(v.getGroupIds(), accessesByGroupId);
            if (!accesses.isEmpty()) {
                for (ItemKey k : expandKeys(v.getScopeCode(), v.getPluginType(),
                        v.getCatalogNames(), v.getDatabaseNames(), v.getTableNames(), v.getColumnNames())) {
                    accessesByKey.computeIfAbsent(k, x -> new HashSet<>()).addAll(accesses);
                    sourcesByKey.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(src);
                }
            }
            scopeByCode.putIfAbsent(v.getScopeCode(), ScopeRef.from(v));
        }

        // 不同 ItemKey (null vs "*") 归一后可能生成同一 policyName,按 (service, name) 二次合并避免后写覆盖。
        Map<PolicyKey, DesiredPolicy> byKey = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Set<String>> e : accessesByKey.entrySet()) {
            ItemKey key = e.getKey();
            ScopeRef svc = scopeByCode.get(key.scopeCode());
            if (svc == null || !svc.enabled()
                    || svc.rangerServiceName() == null || svc.rangerServiceName().isBlank()) {
                log.debug("Skip desired item: serviceCode={} (deleted / disabled / blank ranger service name)",
                        key.scopeCode());
                continue;
            }
            PluginType pluginType = PluginType.parse(svc.pluginType());
            RangerResourceAdapter adapter = adapterRegistry.find(pluginType).orElse(null);
            if (adapter == null) {
                log.debug("Skip desired item: serviceCode={} pluginType={} (no adapter)",
                        key.scopeCode(), svc.pluginType());
                continue;
            }
            try {
                adapter.validateAccesses(e.getValue().stream().sorted().toList());
            } catch (BizException ex) {
                log.warn("Skip desired item with invalid accesses: user={}, service={}, key={}, err={}",
                        userId, key.scopeCode(), key, ex.toString());
                continue;
            }
            List<String> resourcePath = buildResourcePath(adapter.resourceHierarchy(), key);
            String policyName = PolicyNaming.build(adapter.resourceHierarchy(), resourcePath);
            // Ranger 端按 ranger_service_name 定位 service;scope.name 仅为中文展示名,不能下发给 Ranger。
            PolicyKey pk = new PolicyKey(svc.rangerServiceName(), policyName);
            DesiredPolicy existing = byKey.get(pk);
            Set<String> mergedAccesses = new TreeSet<>(e.getValue());
            LinkedHashSet<PermSource> mergedSources = new LinkedHashSet<>(
                    sourcesByKey.getOrDefault(key, new LinkedHashSet<>()));
            if (existing != null) {
                mergedAccesses.addAll(existing.accesses());
                mergedSources.addAll(existing.sources());
            }
            byKey.put(pk, new DesiredPolicy(
                    userId,
                    user.getUsername(),
                    key.scopeCode(),
                    svc.rangerServiceName(),
                    pluginType,
                    policyName,
                    List.copyOf(mergedAccesses),
                    resourcePath,
                    List.copyOf(mergedSources)));
        }
        return new ArrayList<>(byKey.values());
    }

    /** 从 grant view 行收集到的 scope 元数据;同一 scopeCode 跨多 row 字段一致,反向收集 = 反查 scope 表的等价物。 */
    private record ScopeRef(String pluginType, String rangerServiceName, boolean enabled) {

        static ScopeRef from(DataPermBundleStatementResourceView v) {
            return new ScopeRef(v.getPluginType(),
                    v.getRangerServiceName(), Boolean.TRUE.equals(v.getScopeEnabled()));
        }

        static ScopeRef from(DataPermUserDirectGrantResourceView v) {
            return new ScopeRef(v.getPluginType(),
                    v.getRangerServiceName(), Boolean.TRUE.equals(v.getScopeEnabled()));
        }
    }

    /**
     * 一条库表行(各层 JSON 数组)按 plugin 层级笛卡尔积展开成多个单元组 ItemKey。
     * plugin 不含的层 → null;含但为空 → "*";否则逐值展开。
     */
    private List<ItemKey> expandKeys(Long scopeCode, String pluginType,
                                     String catJson, String dbJson, String tblJson, String colJson) {
        List<ResourceLevel> hierarchy = adapterRegistry.find(PluginType.parse(pluginType))
                .map(RangerResourceAdapter::resourceHierarchy).orElse(List.of());
        List<String> cats = slotValues(hierarchy.contains(ResourceLevel.CATALOG), catJson);
        List<String> dbs = slotValues(
                hierarchy.contains(ResourceLevel.DATABASE) || hierarchy.contains(ResourceLevel.SCHEMA), dbJson);
        List<String> tables = slotValues(hierarchy.contains(ResourceLevel.TABLE), tblJson);
        List<String> cols = slotValues(hierarchy.contains(ResourceLevel.COLUMN), colJson);
        List<ItemKey> out = new ArrayList<>();
        for (String[] t : DataPermStatementSupport.cartesian(cats, dbs, tables, cols)) {
            out.add(new ItemKey(scopeCode, t[0], t[1], t[2], t[3]));
        }
        return out;
    }

    /** 该层不适用 → 单元素 [null];适用但空 → ["*"];否则解析数组逐值。 */
    private static List<String> slotValues(boolean applicable, String json) {
        if (!applicable) {
            return Collections.singletonList(null);
        }
        List<String> vals = DataPermStatementSupport.parse(json);
        return vals.isEmpty() ? List.of("*") : vals;
    }

    private static List<String> buildResourcePath(List<ResourceLevel> hierarchy, ItemKey key) {
        List<String> out = new ArrayList<>(hierarchy.size());
        for (ResourceLevel level : hierarchy) {
            String v = switch (level) {
                case CATALOG -> key.catalog();
                case DATABASE -> key.database();
                case SCHEMA -> key.database(); // Trino: schema 对应 Rudder 的 database
                case TABLE -> key.table();
                case COLUMN -> key.column();
            };
            out.add(v == null || v.isBlank() ? "*" : v);
        }
        return out;
    }

    /** groupIds JSON → 各分组 accesses 并集。未知分组(已删但 grant 仍存的过渡态)跳过,返回空集表示无贡献。 */
    private static Set<String> resolveGroupAccesses(String groupIdsJson, Map<Long, List<String>> accessesByGroupId) {
        List<Long> groupIds = DataPermStatementSupport.parseGroupIds(groupIdsJson);
        if (groupIds.isEmpty()) {
            return Set.of();
        }
        Set<String> accesses = new HashSet<>();
        for (Long gid : groupIds) {
            List<String> a = accessesByGroupId.get(gid);
            if (a != null) {
                accesses.addAll(a);
            }
        }
        return accesses;
    }

    /** 一个 role 的全部库表行展开成 (ItemKey → accesses) + scope 元数据。与 user 无关,单轮按 bundleId 算一次后跨用户复用。 */
    private RoleExpansion expandRole(List<DataPermBundleStatementResourceView> views,
                                     Map<Long, List<String>> accessesByGroupId) {
        Map<ItemKey, Set<String>> keyAccesses = new LinkedHashMap<>();
        Map<Long, ScopeRef> scopeByCode = new HashMap<>();
        for (DataPermBundleStatementResourceView v : views) {
            Set<String> accesses = resolveGroupAccesses(v.getGroupIds(), accessesByGroupId);
            if (!accesses.isEmpty()) {
                for (ItemKey k : expandKeys(v.getScopeCode(), v.getPluginType(),
                        v.getCatalogNames(), v.getDatabaseNames(), v.getTableNames(), v.getColumnNames())) {
                    keyAccesses.computeIfAbsent(k, x -> new HashSet<>()).addAll(accesses);
                }
            }
            scopeByCode.putIfAbsent(v.getScopeCode(), ScopeRef.from(v));
        }
        return new RoleExpansion(keyAccesses, scopeByCode);
    }

    private record RoleExpansion(Map<ItemKey, Set<String>> keyAccesses, Map<Long, ScopeRef> scopeByCode) {
    }

    // ---------- merge & diff ----------

    /**
     * 按 (service, policyName) 聚合 per-user DesiredPolicy。同 key 下按 accesses set 分桶 → 每个桶
     * 对应一个 Ranger policyItem;**不同 access 集的 user 在不同桶**,避免取并集导致权限提升
     * (例:admin select+insert / viewer select 不能合并成 [admin, viewer] → [select, insert])。
     *
     * <p>key 含 service 因为 Ranger 同名 policy 跨 service 允许并存(uniqueness 域是 (service, name)),
     * 不能跨 service 合并;diff 也按 (service, name) 比对。
     */
    private Map<PolicyKey, MergedPolicy> mergeAcrossUsers(Map<Long, List<DesiredPolicy>> desiredByUser) {
        record Acc(DesiredPolicy first, Map<List<String>, TreeSet<String>> usersByAccess, Set<Long> userIds) {
        }
        Map<PolicyKey, Acc> accByKey = new LinkedHashMap<>();
        for (Map.Entry<Long, List<DesiredPolicy>> e : desiredByUser.entrySet()) {
            Long uid = e.getKey();
            for (DesiredPolicy d : e.getValue()) {
                PolicyKey key = new PolicyKey(d.rangerServiceName(), d.policyName());
                Acc acc = accByKey.computeIfAbsent(key,
                        k -> new Acc(d, new LinkedHashMap<>(), new HashSet<>()));
                List<String> accessKey = new TreeSet<>(d.accesses()).stream().toList();
                acc.usersByAccess
                        .computeIfAbsent(accessKey, k -> new TreeSet<>())
                        .add(d.username());
                acc.userIds.add(uid);
            }
        }
        Map<PolicyKey, MergedPolicy> out = new LinkedHashMap<>(accByKey.size());
        for (Map.Entry<PolicyKey, Acc> e : accByKey.entrySet()) {
            Acc acc = e.getValue();
            List<MergedPolicy.Bucket> buckets = new ArrayList<>(acc.usersByAccess.size());
            for (Map.Entry<List<String>, TreeSet<String>> b : acc.usersByAccess.entrySet()) {
                buckets.add(new MergedPolicy.Bucket(List.copyOf(b.getValue()), b.getKey()));
            }
            out.put(e.getKey(), new MergedPolicy(
                    acc.first.scopeCode(),
                    acc.first.rangerServiceName(),
                    acc.first.pluginType(),
                    acc.first.policyName(),
                    acc.first.resourcePath(),
                    List.copyOf(buckets),
                    Set.copyOf(acc.userIds)));
        }
        return out;
    }

    private List<Change> diff(Map<PolicyKey, MergedPolicy> desiredByKey,
                              Map<String, Map<String, RangerPolicy>> actualByService) {
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<PolicyKey, MergedPolicy> e : desiredByKey.entrySet()) {
            PolicyKey key = e.getKey();
            MergedPolicy m = e.getValue();
            RangerPolicy a = actualByService.getOrDefault(key.serviceName(), Map.of())
                    .get(key.policyName());
            if (a == null) {
                changes.add(Change.create(m, buildPolicyPayload(m)));
            } else if (!samePolicyContent(a, m)) {
                RangerPolicy payload = buildPolicyPayload(m);
                payload.setId(a.getId());
                changes.add(Change.update(m, a.getId(), payload));
            }
        }
        // orphan DELETE 只对 rudder- 自家 policy 兜底,非 rudder- 是外部资产严禁误删。
        for (Map.Entry<String, Map<String, RangerPolicy>> svcEntry : actualByService.entrySet()) {
            String svc = svcEntry.getKey();
            for (Map.Entry<String, RangerPolicy> e : svcEntry.getValue().entrySet()) {
                if (!PolicyNaming.isRudderPolicy(e.getKey())) {
                    continue;
                }
                if (!desiredByKey.containsKey(new PolicyKey(svc, e.getKey()))) {
                    changes.add(Change.delete(e.getKey(), e.getValue().getId()));
                }
            }
        }
        return changes;
    }

    /** Ranger 端 policy 标识 = (service, policyName) 二元组,policy name 跨 service 可重名。 */
    record PolicyKey(String serviceName, String policyName) {
    }

    private static boolean samePolicyContent(RangerPolicy actual, MergedPolicy desired) {
        List<RangerPolicyItem> items = actual.getPolicyItems();
        if (items == null || items.size() != desired.buckets().size()) {
            return false;
        }
        Set<Set<String>> actualSig = items.stream()
                .map(DataPermReconciler::policyItemSignature)
                .collect(Collectors.toSet());
        Set<Set<String>> desiredSig = desired.buckets().stream()
                .map(b -> bucketSignature(b.users(), b.accesses()))
                .collect(Collectors.toSet());
        return actualSig.equals(desiredSig);
    }

    /** (users, accesses) 二元组归一为可比较的 Set:用前缀区分 user 项与 access 项,避免歧义。 */
    private static Set<String> policyItemSignature(RangerPolicyItem item) {
        Set<String> users = item.getUsers() == null ? Set.of() : new HashSet<>(item.getUsers());
        Set<String> accesses = item.getAccesses() == null
                ? Set.of()
                : item.getAccesses().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getIsAllowed()))
                        .map(RangerPolicyAccess::getType)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return bucketSignature(users, accesses);
    }

    private static Set<String> bucketSignature(Collection<String> users, Collection<String> accesses) {
        Set<String> sig = new HashSet<>(users.size() + accesses.size());
        users.forEach(u -> sig.add("u:" + u));
        accesses.forEach(a -> sig.add("a:" + a));
        return sig;
    }

    private RangerPolicy buildPolicyPayload(MergedPolicy m) {
        RangerResourceAdapter adapter = adapterRegistry.require(m.pluginType());
        // toRangerResource 只读 resource 4 元组,access 走 policyItems(buckets),此 DTO 不带 accesses。
        DataPermPermissionItemDTO item = DataPermPermissionItemDTO.builder()
                .scopeCode(m.scopeCode())
                .catalogName(getOrNull(m.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.CATALOG))
                .databaseName(getOrNullByEither(m.resourcePath(), adapter.resourceHierarchy(),
                        ResourceLevel.DATABASE, ResourceLevel.SCHEMA))
                .tableName(getOrNull(m.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.TABLE))
                .columnName(getOrNull(m.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.COLUMN))
                .build();
        Map<String, RangerPolicyResource> resources = adapter.toRangerResource(item);
        List<RangerPolicyItem> policyItems = m.buckets().stream()
                .map(b -> RangerPolicyItem.builder()
                        .users(b.users())
                        .groups(List.of()).roles(List.of())
                        .accesses(b.accesses().stream()
                                .map(a -> new RangerPolicyAccess(a, true))
                                .toList())
                        .delegateAdmin(false).build())
                .toList();
        return RangerPolicy.builder()
                .name(m.policyName())
                .service(m.rangerServiceName())
                .serviceType(adapter.rangerServiceType())
                .policyType(0)
                .isEnabled(true)
                .isAuditEnabled(true)
                .resources(resources)
                .policyItems(policyItems)
                .build();
    }

    private static String getOrNull(List<String> values, List<ResourceLevel> hierarchy, ResourceLevel target) {
        int idx = hierarchy.indexOf(target);
        return idx < 0 ? null : values.get(idx);
    }

    private static String getOrNullByEither(List<String> values, List<ResourceLevel> hierarchy,
                                            ResourceLevel primary, ResourceLevel fallback) {
        int idx = hierarchy.indexOf(primary);
        if (idx < 0) {
            idx = hierarchy.indexOf(fallback);
        }
        return idx < 0 ? null : values.get(idx);
    }

    // ---------- apply ----------

    private void applyPhase(List<Change> changes,
                            ExecutorService executor,
                            ConcurrentLinkedQueue<FailedChange> failed,
                            AtomicInteger created,
                            AtomicInteger updated,
                            AtomicInteger deleted,
                            Map<String, Map<String, RangerPolicy>> actualByService) {
        if (changes.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = changes.stream()
                .map(c -> CompletableFuture.runAsync(() -> {
                    try {
                        applyChange(c, actualByService);
                        switch (c.kind()) {
                            case CREATE -> created.incrementAndGet();
                            case UPDATE -> updated.incrementAndGet();
                            case DELETE -> deleted.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failed.add(new FailedChange(c, e));
                    }
                }, executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void applyChange(Change c, Map<String, Map<String, RangerPolicy>> actualByService) {
        switch (c.kind()) {
            case CREATE -> {
                try {
                    rangerClient.createPolicy(c.payload());
                } catch (BizException e) {
                    if (e.getErrorCode() == DataPermErrorCode.RANGER_POLICY_RESOURCE_CONFLICT
                            && takeoverConflictingResource(c.payload(), actualByService)) {
                        return;
                    }
                    throw e;
                }
            }
            case UPDATE -> rangerClient.updatePolicy(c.policyId(), c.payload());
            case DELETE -> rangerClient.deletePolicy(c.policyId());
        }
    }

    /**
     * Ranger {@code (service, resource)} 全局唯一。CREATE 撞此约束(常见:Hive plugin 装 service 时自动建的
     * {@code all - database/table/column} 默认 admin policy)→ 同 resource 的现有 policy 改名 {@code rudder-...}
     * 并改写 policyItems,让 Rudder 成为该 resource 唯一授权源。
     *
     * <p>走本轮已拉到的 {@code actualByService} 内存快照避免额外 list REST;并发同 round 内 PolicyKey 唯一,
     * 同 occupying 不会被多 Change 抢(diff 阶段同 resource 已合并为一个 desired)。
     *
     * @return true 表示接管成功;false 表示未找到匹配 resource 的占位者,由 caller 抛回原 CREATE 错。
     */
    private boolean takeoverConflictingResource(RangerPolicy desired,
                                                Map<String, Map<String, RangerPolicy>> actualByService) {
        String svc = desired.getService();
        Map<String, RangerPolicy> svcPolicies = actualByService.getOrDefault(svc, Map.of());
        RangerPolicy occupying = null;
        for (RangerPolicy p : svcPolicies.values()) {
            if (PolicyNaming.isRudderPolicy(p.getName())) {
                continue;
            }
            if (sameResource(p.getResources(), desired.getResources())) {
                occupying = p;
                break;
            }
        }
        if (occupying == null) {
            log.warn("Takeover: no policy matches desired resource on service={}, desiredName={}",
                    svc, desired.getName());
            return false;
        }
        RangerPolicy payload = desired.toBuilder().id(occupying.getId()).build();
        try {
            rangerClient.updatePolicy(occupying.getId(), payload);
        } catch (BizException e) {
            throw new BizException(DataPermErrorCode.RANGER_POLICY_TAKEOVER_FAILED, e, e.getMessage());
        }
        log.info("Takeover Ranger policy: service={}, id={}, oldName={}, newName={}, "
                + "overriddenItems={}, newItems={}",
                svc, occupying.getId(), occupying.getName(), desired.getName(),
                occupying.getPolicyItems() == null ? 0 : occupying.getPolicyItems().size(),
                desired.getPolicyItems() == null ? 0 : desired.getPolicyItems().size());
        return true;
    }

    /** Ranger 返回的 isExcludes / isRecursive 默认 null,Rudder build 出来是 false,直接 equals 会误判,故归一为 boolean。 */
    private static boolean sameResource(Map<String, RangerPolicyResource> a,
                                        Map<String, RangerPolicyResource> b) {
        if (a == null || b == null || !a.keySet().equals(b.keySet())) {
            return false;
        }
        for (String k : a.keySet()) {
            RangerPolicyResource la = a.get(k);
            RangerPolicyResource lb = b.get(k);
            Set<String> va = new HashSet<>(la.getValues() == null ? List.of() : la.getValues());
            Set<String> vb = new HashSet<>(lb.getValues() == null ? List.of() : lb.getValues());
            if (!va.equals(vb)
                    || Boolean.TRUE.equals(la.getIsExcludes()) != Boolean.TRUE.equals(lb.getIsExcludes())
                    || Boolean.TRUE.equals(la.getIsRecursive()) != Boolean.TRUE.equals(lb.getIsRecursive())) {
                return false;
            }
        }
        return true;
    }

    private synchronized ExecutorService ensureWriteExecutor(int desiredConcurrency) {
        int n = Math.max(1, desiredConcurrency);
        if (writeExecutor != null && currentConcurrency == n) {
            return writeExecutor;
        }
        // 让旧池跑完手头任务自然回收,不阻塞 reconfigure 调用方。
        if (writeExecutor != null) {
            writeExecutor.shutdown();
        }
        writeExecutor = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "dataperm-reconcile");
            t.setDaemon(true);
            return t;
        });
        currentConcurrency = n;
        return writeExecutor;
    }

    @jakarta.annotation.PreDestroy
    public void shutdownExecutor() {
        if (writeExecutor != null) {
            writeExecutor.shutdown();
            try {
                if (!writeExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    writeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeExecutor.shutdownNow();
            }
        }
    }

    // ---------- alert ----------

    void evaluateRound(ReconcileStats stats, DataPermConfigDTO config) {
        if (stats.isFullFailure()) {
            int count = consecutiveFullFailureCount.incrementAndGet();
            int threshold = Math.max(1, config.getReconcileFailureAlertThreshold());
            if (count >= threshold && !alertedRangerDown) {
                alertedRangerDown = true;
                try {
                    notificationService.notify(
                            io.github.zzih.rudder.service.dataperm.notification.DataPermNotifications
                                    .syncFailure(count, stats.lastError()));
                } catch (Exception e) {
                    log.error("Failed to send RECONCILE_RANGER_DOWN alert", e);
                }
            }
        } else {
            consecutiveFullFailureCount.set(0);
            alertedRangerDown = false;
            if (stats.isPartialFailure()) {
                log.warn("Reconcile partial failure (no alert, self-healing next round): {}", stats);
            }
        }
    }

    int peekConsecutiveFailureCount() {
        return consecutiveFullFailureCount.get();
    }

    boolean peekAlertedRangerDown() {
        return alertedRangerDown;
    }

    // ---------- effective snapshot ----------

    /**
     * 跟最近一份 snapshot 比对,有差异才写新 version。
     * 空 desired 且上版本已是 sentinel → 跳过;空 desired 且上版本是有权 → 写 sentinel 一行表示"V_n+1 当前 0 权限"。
     */
    private void writeSnapshotIfChanged(Long userId, List<DesiredPolicy> desired, LocalDateTime snapshotTime) {
        List<DesiredPolicy> safe = desired == null ? List.of() : desired;
        String currentKey = canonicalUserKey(safe);
        if (currentKey.equals(lastCanonicalKey.get(userId))) {
            return;
        }
        List<DataPermUserEffectiveSnapshot> latest = userEffectiveSnapshotDao.selectLatest(userId);
        Long latestVersion = latest.isEmpty() ? null : latest.get(0).getVersion();
        if (sameSnapshot(safe, latest)) {
            lastCanonicalKey.put(userId, currentKey);
            return;
        }
        long newVersion = latestVersion == null ? 1L : latestVersion + 1L;
        List<DataPermUserEffectiveSnapshot> rows = safe.isEmpty()
                ? List.of(buildSentinelRow(userId, newVersion, snapshotTime))
                : safe.stream().map(d -> toSnapshotRow(d, userId, newVersion, snapshotTime)).toList();
        userEffectiveSnapshotDao.insertBatch(rows);
        lastCanonicalKey.put(userId, currentKey);
        log.debug("User effective snapshot written: user={}, version={}, perms={}",
                userId, newVersion, safe.size());
    }

    /** scope_code IS NULL 标识"该 version 是 0 权限占位",鉴权 / 审计 query 过滤掉。 */
    private static DataPermUserEffectiveSnapshot buildSentinelRow(Long userId, long version, LocalDateTime t) {
        DataPermUserEffectiveSnapshot row = new DataPermUserEffectiveSnapshot();
        row.setUserId(userId);
        row.setVersion(version);
        row.setSnapshotTime(t);
        row.setAccesses("[]");
        row.setSourceKinds("[]");
        return row;
    }

    private static boolean isSentinelRow(DataPermUserEffectiveSnapshot row) {
        return row.getScopeCode() == null;
    }

    private static String canonicalUserKey(List<DesiredPolicy> desired) {
        return desired.stream()
                .map(DataPermReconciler::canonicalFromDesired)
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private DataPermUserEffectiveSnapshot toSnapshotRow(DesiredPolicy d, Long userId,
                                                        long version, LocalDateTime snapshotTime) {
        RangerResourceAdapter adapter = adapterRegistry.require(d.pluginType());
        DataPermUserEffectiveSnapshot row = new DataPermUserEffectiveSnapshot();
        row.setUserId(userId);
        row.setVersion(version);
        row.setSnapshotTime(snapshotTime);
        row.setScopeCode(d.scopeCode());
        row.setCatalogName(getOrNull(d.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.CATALOG));
        row.setDatabaseName(getOrNullByEither(d.resourcePath(), adapter.resourceHierarchy(),
                ResourceLevel.DATABASE, ResourceLevel.SCHEMA));
        row.setTableName(getOrNull(d.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.TABLE));
        row.setColumnName(getOrNull(d.resourcePath(), adapter.resourceHierarchy(), ResourceLevel.COLUMN));
        row.setAccesses(JsonUtils.toJson(d.accesses()));
        row.setSourceKinds(JsonUtils.toJson(d.sources()));
        return row;
    }

    /**
     * desired 与 snapshot 行集是否完全一致。
     * 空 desired ↔ 上版本是 sentinel 也算一致(表示"上轮已是 0 权限,本轮仍 0 权限",无需写新版本)。
     */
    private static boolean sameSnapshot(List<DesiredPolicy> desired, List<DataPermUserEffectiveSnapshot> rows) {
        if (desired.isEmpty()) {
            return rows.size() == 1 && isSentinelRow(rows.get(0));
        }
        if (desired.size() != rows.size()) {
            return false;
        }
        Set<String> desiredKeys = desired.stream()
                .map(DataPermReconciler::canonicalFromDesired)
                .collect(Collectors.toSet());
        Set<String> snapKeys = rows.stream()
                .filter(r -> !isSentinelRow(r))
                .map(DataPermReconciler::canonicalFromSnapshot)
                .collect(Collectors.toSet());
        return desiredKeys.equals(snapKeys);
    }

    private static String canonicalFromDesired(DesiredPolicy d) {
        return canonical(d.scopeCode(), d.resourcePath(), d.accesses(), d.sources());
    }

    /** snapshot path 列仅记录 adapter.hierarchy 中存在的 level,filter(nonNull) 后与 desired.resourcePath 同形。 */
    private static String canonicalFromSnapshot(DataPermUserEffectiveSnapshot s) {
        List<String> accesses = JsonUtils.toList(s.getAccesses(), String.class);
        List<PermSource> sources = JsonUtils.toList(s.getSourceKinds(), PermSource.class);
        List<String> path = Stream.of(s.getCatalogName(), s.getDatabaseName(),
                s.getTableName(), s.getColumnName())
                .filter(Objects::nonNull)
                .toList();
        return canonical(s.getScopeCode(), path,
                accesses == null ? List.of() : accesses,
                sources == null ? List.of() : sources);
    }

    private static String canonical(Long serviceCode, List<String> pathSegments,
                                    List<String> accesses, List<PermSource> sources) {
        List<String> sortedAccesses = new ArrayList<>(accesses);
        Collections.sort(sortedAccesses);
        List<String> sortedSources = sources.stream()
                .map(PermSource::canonical)
                .sorted()
                .toList();
        return serviceCode + "|"
                + String.join("/", pathSegments) + "|"
                + String.join(",", sortedAccesses) + "|"
                + String.join(",", sortedSources);
    }

    // ---------- helpers ----------

    record ItemKey(Long scopeCode, String catalog, String database, String table, String column) {
    }

    /** userIds 用于失败时反推受影响 user;DELETE 不关联具体 user,固定空集。 */
    record Change(Kind kind, Set<Long> userIds, String policyName, Long policyId, RangerPolicy payload) {

        enum Kind {
            CREATE, UPDATE, DELETE
        }

        static Change create(MergedPolicy m, RangerPolicy payload) {
            return new Change(Kind.CREATE, m.userIds(), m.policyName(), null, payload);
        }

        static Change update(MergedPolicy m, Long id, RangerPolicy payload) {
            return new Change(Kind.UPDATE, m.userIds(), m.policyName(), id, payload);
        }

        static Change delete(String policyName, Long id) {
            return new Change(Kind.DELETE, Set.of(), policyName, id, null);
        }
    }

    record FailedChange(Change change, Throwable error) {
    }
}
