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

package io.github.zzih.rudder.rerank.api.plugin;

import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.spi.RerankClientFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import org.springframework.stereotype.Component;

/** Rerank 插件注册表。 */
@Component
public class RerankPluginManager
        extends
            AbstractConfigurablePluginRegistry<ProviderContext, RerankClientFactory<?>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RerankPluginManager(ProviderContext providerContext) {
        super((Class) RerankClientFactory.class, providerContext, "rerank");
    }

    public RerankClient create(String provider, String providerParamsJson) {
        return doCreate(requireFactory(provider), providerParamsJson);
    }

    private <P> RerankClient doCreate(RerankClientFactory<P> factory, String json) {
        P props = deserializeProps(factory, json);
        return factory.create(providerContext, props);
    }
}
