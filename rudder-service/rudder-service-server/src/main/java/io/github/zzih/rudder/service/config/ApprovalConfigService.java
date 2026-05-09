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

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.plugin.ApprovalPluginManager;
import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/** Approval active 实例的访问入口。未配置时 fallback 到 LOCAL provider。 */
@Service
public class ApprovalConfigService extends AbstractConfigService<ApprovalNotifier> {

    private final ApprovalPluginManager pluginManager;

    public ApprovalConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                                 ApprovalPluginManager pluginManager) {
        super(cache, GlobalCacheKey.APPROVAL, ConfigErrorCode.APPROVAL_NOT_CONFIGURED, spiConfigDao, SpiType.APPROVAL);
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected ApprovalNotifier buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(ApprovalNotifier instance) {
        return instance.healthCheck();
    }

    @Override
    protected String fallbackProvider() {
        return pluginManager.hasFallback() ? ApprovalPluginManager.FALLBACK_PROVIDER : null;
    }
}
