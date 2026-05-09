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

package io.github.zzih.rudder.approval.api.plugin;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.spi.ApprovalNotifierFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import org.springframework.stereotype.Component;

/**
 * Approval 插件注册表。只暴露工厂能力(create),不持 active 状态。当前生效的 ApprovalNotifier 由上层
 * {@code ApprovalConfigService} 管理。
 */
@Component
public class ApprovalPluginManager
        extends
            AbstractConfigurablePluginRegistry<ProviderContext, ApprovalNotifierFactory<?>> {

    public static final String FALLBACK_PROVIDER = "LOCAL";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ApprovalPluginManager(ProviderContext providerContext) {
        super((Class) ApprovalNotifierFactory.class, providerContext, "approval");
    }

    /** 用 provider + JSON 参数造一个 active ApprovalNotifier 实例。 */
    public ApprovalNotifier create(String provider, String providerParamsJson) {
        return doCreate(requireFactory(provider), providerParamsJson);
    }

    private <P> ApprovalNotifier doCreate(ApprovalNotifierFactory<P> factory, String json) {
        P props = deserializeProps(factory, json);
        return factory.create(providerContext, props);
    }

    public boolean hasFallback() {
        return factories.containsKey(FALLBACK_PROVIDER);
    }
}
