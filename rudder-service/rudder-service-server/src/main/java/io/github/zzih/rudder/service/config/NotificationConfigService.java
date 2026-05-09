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

package io.github.zzih.rudder.service.config;

import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.plugin.NotificationPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/**
 * 读路径委托 {@link NotificationService}:共用 {@link GlobalCacheKey#NOTIFICATION},
 * 该 key 的 cache value 已被 NotificationService 占为 {@link NotificationService.Active} record,
 * 这里若再走基类 cache.getOrLoad 拿 NotificationSender 会触发 ClassCastException。
 */
@Service
public class NotificationConfigService extends AbstractConfigService<NotificationSender> {

    private final NotificationPluginManager pluginManager;
    private final NotificationService notificationService;

    public NotificationConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                                     NotificationPluginManager pluginManager,
                                     NotificationService notificationService) {
        super(cache, GlobalCacheKey.NOTIFICATION, ConfigErrorCode.NOTIFICATION_NOT_CONFIGURED, spiConfigDao,
                SpiType.NOTIFICATION);
        this.pluginManager = pluginManager;
        this.notificationService = notificationService;
    }

    @Override
    public NotificationSender active() {
        NotificationService.Active a = notificationService.current();
        return a == null ? null : a.sender();
    }

    @Override
    public HealthStatus health() {
        return notificationService.health();
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected NotificationSender buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(NotificationSender instance) {
        return instance.healthCheck();
    }
}
