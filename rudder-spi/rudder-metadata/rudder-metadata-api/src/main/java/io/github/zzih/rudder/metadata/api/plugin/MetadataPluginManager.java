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

package io.github.zzih.rudder.metadata.api.plugin;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.spi.MetadataClientFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Metadata 插件注册表。**只暴露工厂能力**（create / fallback），不持 active 状态。
 * 当前生效的 MetadataClient 由上层 {@code MetadataConfigService} 管理。
 */
@Component
public class MetadataPluginManager
        extends
            AbstractConfigurablePluginRegistry<ProviderContext, MetadataClientFactory> {

    public static final String FALLBACK_PROVIDER = "JDBC";

    public MetadataPluginManager(ProviderContext providerContext) {
        super(MetadataClientFactory.class, providerContext, "metadata");
    }

    /** 用 provider + 配置造一个 MetadataClient 实例。无状态，纯工厂方法。 */
    public MetadataClient create(String provider, Map<String, String> config) {
        MetadataClientFactory factory = requireFactory(provider);
        Map<String, String> merged = new HashMap<>(config != null ? config : Map.of());
        return factory.create(providerContext, merged);
    }

    /** 当前是否注册了 fallback provider（默认 JDBC）。 */
    public boolean hasFallback() {
        return factories.containsKey(FALLBACK_PROVIDER);
    }
}
