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

package io.github.zzih.rudder.service.registry;

import io.github.zzih.rudder.dao.dao.ServiceRegistryDao;
import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.notification.api.model.NodeInfo;
import io.github.zzih.rudder.service.coordination.lock.LeaderLockService;
import io.github.zzih.rudder.service.coordination.registry.NodeRegistryService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Leader 单独承担:
 * <ul>
 *   <li>DB 写 ONLINE 但 Redis key 已 TTL 过期 → CAS 翻 OFFLINE + 发通知</li>
 *   <li>OFFLINE 超 {@link #CLEANUP_AFTER} 未恢复 → 物理删除,防 pod IP 漂移导致 DB 膨胀</li>
 * </ul>
 *
 * <p>选举走 {@link LeaderLockService}(coordination 层原语),抢占 / 续约原子。
 * 租约 ≥ 2 倍 tick 周期,leader 漏 1 次 tick 不会失主。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryReconciler {

    private static final String RESOURCE = "registry";
    private static final Duration LEASE_TTL = Duration.ofSeconds(15);
    private static final Duration CLEANUP_AFTER = Duration.ofHours(1);

    private static final String OWNER_ID = UUID.randomUUID().toString();

    private final LeaderLockService leaderLock;
    private final NodeRegistryService nodeRegistry;
    private final ServiceRegistryDao serviceRegistryDao;
    private final ServiceRegistryService registryService;

    @Scheduled(fixedRate = 5_000, initialDelay = 30_000)
    public void tick() {
        if (!leaderLock.tryAcquireOrRenew(RESOURCE, OWNER_ID, LEASE_TTL)) {
            return;
        }
        try {
            reconcileStaleOnline();
            cleanupOldOffline();
        } catch (Exception e) {
            log.warn("Registry reconcile error", e);
        }
    }

    private void reconcileStaleOnline() {
        List<ServiceRegistry> online = serviceRegistryDao.selectAllOnline();
        if (online.isEmpty()) {
            return;
        }
        Set<String> alive = nodeRegistry.aliveKeys();
        LocalDateTime now = LocalDateTime.now();
        List<NodeInfo> flipped = new ArrayList<>();
        for (ServiceRegistry row : online) {
            String expectedKey = nodeRegistry.keyOf(row.getType(), row.getHost(), row.getPort());
            if (alive.contains(expectedKey)) {
                continue;
            }
            int affected = serviceRegistryDao.markOfflineIfOnline(
                    row.getType(), row.getHost(), row.getPort(), now);
            if (affected > 0) {
                flipped.add(new NodeInfo(row.getType().name(), row.getHost(), row.getPort()));
            }
        }
        if (!flipped.isEmpty()) {
            log.warn("Reconciled {} stale ONLINE node(s) to OFFLINE", flipped.size());
            registryService.notifyOffline(flipped);
        }
    }

    private void cleanupOldOffline() {
        LocalDateTime threshold = LocalDateTime.now().minus(CLEANUP_AFTER);
        int deleted = serviceRegistryDao.deleteOfflineOlderThan(threshold);
        if (deleted > 0) {
            log.info("Cleaned up {} old OFFLINE registry row(s)", deleted);
        }
    }
}
