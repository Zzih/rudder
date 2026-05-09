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

package io.github.zzih.rudder.ai.rerank;

import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.plugin.RerankPluginManager;
import io.github.zzih.rudder.service.config.AbstractConfigService;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/** RerankClient active 实例的访问入口。 */
@Service
public class RerankConfigService extends AbstractConfigService<RerankClient> {

    private final RerankPluginManager pluginManager;

    public RerankConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                               RerankPluginManager pluginManager) {
        super(cache, GlobalCacheKey.RERANK, ConfigErrorCode.RERANK_NOT_CONFIGURED, spiConfigDao, SpiType.RERANK);
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected RerankClient buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(RerankClient instance) {
        return instance.healthCheck();
    }
}
