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

package io.github.zzih.rudder.mcp.event;

import io.github.zzih.rudder.dao.dao.McpTokenDao;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时把已过期但 status 仍为 ACTIVE 的 token 标记为 EXPIRED。
 *
 * <p>认证路径 {@code McpTokenService.verify} 已对 expiresAt 做实时检查，所以即使状态不一致
 * 也不影响安全。这个任务的目的是让 UI / 审计 看到的 token 状态与实际生效状态一致。
 *
 * <p>调度间隔 30 分钟，单次最多扫 200 行（防御性上限，正常环境量级远小于此）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class McpTokenExpiryScheduler {

    private static final int BATCH_LIMIT = 200;

    private final McpTokenDao tokenDao;

    @Scheduled(cron = "0 */30 * * * *")
    public void markExpired() {
        List<Long> ids = tokenDao.selectExpiredActiveIds(BATCH_LIMIT);
        if (ids.isEmpty()) {
            return;
        }
        int marked = 0;
        for (Long id : ids) {
            if (tokenDao.markExpiredIfActive(id) > 0) {
                marked++;
            }
        }
        log.info("MCP token expiry scan: scanned={}, marked={}", ids.size(), marked);
    }
}
