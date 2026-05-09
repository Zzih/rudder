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
import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.TaskFactory;
import io.github.zzih.rudder.runtime.api.plugin.RuntimePluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * EngineRuntime active 实例的访问入口。{@link #onProviderChanged} 在切换 provider 时关旧 SDK 资源
 * (Aliyun tea-openapi 池 / AWS SDK client 等)。
 */
@Service
public class RuntimeConfigService extends AbstractConfigService<EngineRuntime> {

    private final RuntimePluginManager pluginManager;

    public RuntimeConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                                RuntimePluginManager pluginManager) {
        super(cache, GlobalCacheKey.RUNTIME, ConfigErrorCode.RUNTIME_NOT_CONFIGURED, spiConfigDao, SpiType.RUNTIME);
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected EngineRuntime buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(EngineRuntime instance) {
        return instance.healthCheck();
    }

    @Override
    protected void onProviderChanged(String previousProvider, String newProvider) {
        if (previousProvider != null && !previousProvider.equalsIgnoreCase(newProvider)) {
            pluginManager.closeResources(previousProvider);
        }
    }

    public Map<String, String> envVars() {
        EngineRuntime r = active();
        return r != null ? r.envVars() : Map.of();
    }

    public Optional<TaskFactory> taskFactoryFor(TaskType type) {
        EngineRuntime r = active();
        return r != null ? r.taskFactoryFor(type) : Optional.empty();
    }
}
