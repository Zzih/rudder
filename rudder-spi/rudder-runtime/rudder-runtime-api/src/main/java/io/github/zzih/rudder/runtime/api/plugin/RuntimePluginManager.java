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

package io.github.zzih.rudder.runtime.api.plugin;

import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.spi.EngineRuntimeProvider;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Runtime 插件注册表。**只暴露工厂能力**({@link #create}),不持 active 状态。
 * 当前 active EngineRuntime 由 {@code RuntimeConfigService} 通过 {@code GlobalCacheService} 管理。
 */
@Slf4j
@Component
public class RuntimePluginManager extends AbstractConfigurablePluginRegistry<ProviderContext, EngineRuntimeProvider> {

    public RuntimePluginManager(ProviderContext providerContext) {
        super(EngineRuntimeProvider.class, providerContext, "runtime");
    }

    /** 用 provider + 配置造一个 active EngineRuntime。无状态,纯工厂方法。 */
    public EngineRuntime create(String provider, Map<String, String> params) {
        EngineRuntimeProvider rp = requireFactory(provider);
        EngineRuntime runtime = rp.create(providerContext,
                new HashMap<>(params != null ? params : Map.of()));
        log.info("Built active runtime: provider={}", normalize(provider));
        return runtime;
    }

    /** 关闭 provider 持有的共享资源(SDK client 池等)。配置切换 / 关停时由上层调用。 */
    public void closeResources(String provider) {
        if (provider == null) {
            return;
        }
        EngineRuntimeProvider rp = factories.get(normalize(provider));
        if (rp == null) {
            return;
        }
        try {
            rp.closeResources();
        } catch (Exception e) {
            log.warn("Failed to close resources for runtime provider {}: {}", provider, e.getMessage());
        }
    }
}
