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

package io.github.zzih.rudder.vector.api.plugin;

import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.vector.api.VectorStore;
import io.github.zzih.rudder.vector.api.VectorStoreFactory;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Vector 插件注册表。**只暴露工厂能力**（create / closeStore），不持 active 状态。
 * 当前生效的 VectorStore 由上层 {@code VectorConfigService} 管理。
 */
@Component
public class VectorPluginManager
        extends
            AbstractConfigurablePluginRegistry<ProviderContext, VectorStoreFactory> {

    public VectorPluginManager(ProviderContext providerContext) {
        super(VectorStoreFactory.class, providerContext, "vector");
    }

    /** 用 provider + 配置造一个 VectorStore 实例。无状态，纯工厂方法。 */
    public VectorStore create(String provider, Map<String, String> config) {
        return requireFactory(provider).create(providerContext, config != null ? config : Map.of());
    }
}
