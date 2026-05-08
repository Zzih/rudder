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

import io.github.zzih.rudder.dao.dao.NotificationConfigDao;
import io.github.zzih.rudder.dao.entity.NotificationConfig;
import io.github.zzih.rudder.service.config.dto.NotificationConfigDTO;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Notification active 配置的 server 侧入口：复用 {@link NotificationService} 的缓存做读，自己负责写入和失效。 */
@Service
@RequiredArgsConstructor
public class NotificationConfigService {

    private final GlobalCacheService cache;
    private final NotificationConfigDao dao;
    private final NotificationService notificationService;

    public HealthStatus health() {
        return notificationService.health();
    }

    public void save(NotificationConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
        cache.invalidate(GlobalCacheKey.NOTIFICATION);
    }

    /** Controller 入口：DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(NotificationConfigDTO body) {
        NotificationConfig c = dao.selectActive();
        if (c == null) {
            c = new NotificationConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }
}
