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
import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.plugin.PublishPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/** Publish active 实例的访问入口。无 fallback;未配置时 {@link #required()} 抛 PUBLISH_SERVICE_UNAVAILABLE。 */
@Service
public class PublishConfigService extends AbstractConfigService<Publisher> {

    private final PublishPluginManager pluginManager;

    public PublishConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                                PublishPluginManager pluginManager) {
        // 基类 notConfiguredCode 是兜底;Publish 业务方期望 WorkflowErrorCode.PUBLISH_SERVICE_UNAVAILABLE,override required。
        super(cache, GlobalCacheKey.PUBLISH, ConfigErrorCode.PUBLISH_NOT_CONFIGURED, spiConfigDao, SpiType.PUBLISH);
        this.pluginManager = pluginManager;
    }

    @Override
    public Publisher required() {
        Publisher p = active();
        if (p == null) {
            throw new BizException(WorkflowErrorCode.PUBLISH_SERVICE_UNAVAILABLE);
        }
        return p;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected Publisher buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(Publisher instance) {
        return instance.healthCheck();
    }
}
