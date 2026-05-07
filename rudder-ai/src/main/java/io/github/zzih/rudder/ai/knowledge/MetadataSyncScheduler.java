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

package io.github.zzih.rudder.ai.knowledge;

import io.github.zzih.rudder.dao.entity.AiMetadataSyncConfig;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 每分钟扫描启用 cron 的元数据同步配置,按 cron 到期触发。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataSyncScheduler {

    private final MetadataSyncService syncService;

    private final ConcurrentMap<Long, LocalDateTime> nextFire = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (AiMetadataSyncConfig c : syncService.listScheduled()) {
            try {
                handle(c, now);
            } catch (Exception e) {
                log.warn("metadata sync scheduler {} failed: {}", c.getId(), e.getMessage());
            }
        }
    }

    private void handle(AiMetadataSyncConfig c, LocalDateTime now) {
        String cron = c.getScheduleCron();
        if (cron == null || cron.isBlank() || !CronExpression.isValidExpression(cron)) {
            return;
        }
        CronExpression expr = CronExpression.parse(cron);
        LocalDateTime next = nextFire.get(c.getId());
        if (next == null) {
            LocalDateTime base = c.getLastSyncAt() != null ? c.getLastSyncAt() : now.minusMinutes(1);
            next = expr.next(base);
            if (next == null) {
                return;
            }
            nextFire.put(c.getId(), next);
        }
        if (!now.isBefore(next)) {
            log.info("metadata sync scheduler triggering config {} (datasource {})", c.getId(), c.getDatasourceId());
            syncService.sync(c);
            LocalDateTime after = expr.next(now);
            if (after != null) {
                nextFire.put(c.getId(), after);
            } else {
                nextFire.remove(c.getId());
            }
        }
    }
}
