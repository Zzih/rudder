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
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.MetadataConfigDao;
import io.github.zzih.rudder.dao.entity.MetadataConfig;
import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.plugin.MetadataPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Metadata active 实例的访问入口。未配置时 fallback 到 JDBC（如有）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataConfigService {

    private final GlobalCacheService cache;
    private final MetadataConfigDao dao;
    private final MetadataPluginManager pluginManager;

    public MetadataClient active() {
        return cache.getOrLoad(GlobalCacheKey.METADATA, this::build);
    }

    public MetadataClient required() {
        MetadataClient c = active();
        if (c == null) {
            throw new BizException(ConfigErrorCode.METADATA_NOT_CONFIGURED);
        }
        return c;
    }

    public void save(MetadataConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
        cache.invalidate(GlobalCacheKey.METADATA);
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ProviderConfigDTO body) {
        MetadataConfig c = dao.selectActive();
        if (c == null) {
            c = new MetadataConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }

    public HealthStatus health() {
        MetadataClient c = active();
        return c == null ? HealthStatus.unknown() : c.healthCheck();
    }

    private MetadataClient build() {
        MetadataConfig c = dao.selectActive();
        if (c != null && Boolean.TRUE.equals(c.getEnabled()) && c.getProvider() != null) {
            Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
            return pluginManager.create(c.getProvider(), params);
        }
        if (pluginManager.hasFallback()) {
            try {
                return pluginManager.create(MetadataPluginManager.FALLBACK_PROVIDER, Map.of());
            } catch (Exception e) {
                log.warn("Metadata fallback provider {} cannot be initialized: {}",
                        MetadataPluginManager.FALLBACK_PROVIDER, e.getMessage());
            }
        }
        return null;
    }
}
