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

package io.github.zzih.rudder.service.notification;

import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.notification.api.plugin.NotificationPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知发送入口。Active sender 走 {@link GlobalCacheKey#NOTIFICATION} 共享缓存——
 * 配置变更时由 NotificationConfigService.saveDetail 触发 invalidate，多节点共享失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public record Active(String provider, NotificationSender sender) {
    }

    private final NotificationPluginManager pluginManager;
    private final SpiConfigDao spiConfigDao;
    private final GlobalCacheService cache;

    @Async
    public void notify(NotificationMessage message) {
        Active active = current();
        if (active == null) {
            log.debug("通知未启用, eventType={}", message.eventType());
            return;
        }
        try {
            log.info("发送通知, provider={}, eventType={}", active.provider(), message.eventType());
            active.sender().send(message);
        } catch (Exception e) {
            log.error("Failed to send notification: provider={}, eventType={}",
                    active.provider(), message.eventType(), e);
        }
    }

    /** 当前 active sender；无配置返回 null。 */
    public Active current() {
        return cache.getOrLoad(GlobalCacheKey.NOTIFICATION, this::build);
    }

    public HealthStatus health() {
        Active a = current();
        return a == null ? HealthStatus.unknown() : a.sender().healthCheck();
    }

    private Active build() {
        SpiConfig c = spiConfigDao.selectActive(SpiType.NOTIFICATION);
        if (c == null || !Boolean.TRUE.equals(c.getEnabled()) || c.getProvider() == null) {
            return null;
        }
        return new Active(c.getProvider(), pluginManager.create(c.getProvider(), c.getProviderParams()));
    }
}
