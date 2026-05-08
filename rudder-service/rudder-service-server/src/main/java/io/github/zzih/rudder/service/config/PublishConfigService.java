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

import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.PublishConfigDao;
import io.github.zzih.rudder.dao.entity.PublishConfig;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.plugin.PublishPluginManager;
import io.github.zzih.rudder.service.config.dto.PublishConfigDTO;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Publish active 实例的访问入口。无 fallback：未配置时 {@link #required()} 抛 PUBLISH_SERVICE_UNAVAILABLE。 */
@Service
@RequiredArgsConstructor
public class PublishConfigService {

    /** 缓存 provider + publisher 组合，避免 activeProvider 单独打 DB。 */
    public record Active(String provider, Publisher publisher) {
    }

    private final GlobalCacheService cache;
    private final PublishConfigDao dao;
    private final PublishPluginManager pluginManager;

    public Publisher active() {
        Active a = current();
        return a == null ? null : a.publisher();
    }

    public Publisher required() {
        Publisher p = active();
        if (p == null) {
            throw new BizException(WorkflowErrorCode.PUBLISH_SERVICE_UNAVAILABLE);
        }
        return p;
    }

    public String activeProvider() {
        Active a = current();
        return a == null ? null : a.provider();
    }

    public void save(PublishConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
        cache.invalidate(GlobalCacheKey.PUBLISH);
    }

    /** Controller 入口：DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(PublishConfigDTO body) {
        PublishConfig c = dao.selectActive();
        if (c == null) {
            c = new PublishConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }

    public HealthStatus health() {
        Active a = current();
        return a == null ? HealthStatus.unknown() : a.publisher().healthCheck();
    }

    private Active current() {
        return cache.getOrLoad(GlobalCacheKey.PUBLISH, this::build);
    }

    private Active build() {
        PublishConfig c = dao.selectActive();
        if (c != null && Boolean.TRUE.equals(c.getEnabled()) && c.getProvider() != null) {
            Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
            return new Active(c.getProvider(), pluginManager.create(c.getProvider(), params));
        }
        return null;
    }
}
