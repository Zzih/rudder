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
import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.embedding.api.plugin.EmbeddingPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/** EmbeddingClient active 实例的访问入口。 */
@Service
public class EmbeddingConfigService extends AbstractConfigService<EmbeddingClient> {

    private final EmbeddingPluginManager pluginManager;

    public EmbeddingConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                                  EmbeddingPluginManager pluginManager) {
        super(cache, GlobalCacheKey.EMBEDDING, ConfigErrorCode.EMBEDDING_NOT_CONFIGURED, spiConfigDao,
                SpiType.EMBEDDING);
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected EmbeddingClient buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(EmbeddingClient instance) {
        return instance.healthCheck();
    }
}
