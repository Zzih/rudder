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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiConfigDao;
import io.github.zzih.rudder.dao.entity.AiConfig;
import io.github.zzih.rudder.dao.enums.AiConfigType;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.vector.api.VectorStore;
import io.github.zzih.rudder.vector.api.plugin.VectorPluginManager;

import java.util.Map;

import org.springframework.stereotype.Service;

/** VectorStore active 实例的访问入口。复用 AiConfig 表，{@code type=VECTOR}。 */
@Service
public class VectorConfigService extends AbstractConfigService<AiConfig, VectorStore> {

    private final AiConfigDao dao;
    private final VectorPluginManager pluginManager;

    public VectorConfigService(GlobalCacheService cache, AiConfigDao dao, VectorPluginManager pluginManager) {
        super(cache, GlobalCacheKey.VECTOR, ConfigErrorCode.VECTOR_NOT_CONFIGURED);
        this.dao = dao;
        this.pluginManager = pluginManager;
    }

    @Override
    protected VectorStore build() {
        AiConfig c = dao.selectActive(AiConfigType.VECTOR);
        if (c == null || !Boolean.TRUE.equals(c.getEnabled()) || c.getProvider() == null) {
            return null;
        }
        Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
        return pluginManager.create(c.getProvider(), params);
    }

    @Override
    protected void doUpsert(AiConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
    }

    @Override
    protected HealthStatus healthOf(VectorStore instance) {
        return instance.healthCheck();
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ProviderConfigDTO body) {
        AiConfig c = dao.selectActive(AiConfigType.VECTOR);
        if (c == null) {
            c = new AiConfig();
            c.setType(AiConfigType.VECTOR);
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }
}
