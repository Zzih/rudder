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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultProperties;
import io.github.zzih.rudder.result.api.plugin.ResultPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/**
 * ResultFormat active 实例的访问入口。所有 5 个 provider(PARQUET/CSV/JSON/ORC/AVRO)共用
 * {@link ResultProperties}(含 {@code defaultQueryRows}),走 admin UI 提交的 JSON 直接是
 * {@code ResultProperties} 序列化形态,框架反序列化派发即可。
 */
@Service
public class ResultConfigService extends AbstractConfigService<ResultFormat> {

    private final SpiConfigDao spiConfigDao;
    private final ResultPluginManager pluginManager;

    public ResultConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                               ResultPluginManager pluginManager) {
        super(cache, GlobalCacheKey.RESULT, ConfigErrorCode.RESULT_NOT_CONFIGURED, spiConfigDao, SpiType.RESULT);
        this.spiConfigDao = spiConfigDao;
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected ResultFormat buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(ResultFormat instance) {
        return instance.healthCheck();
    }

    /** SQL 任务 setMaxRows 默认值。{@link ResultProperties} 的 canonical constructor 兜底非正整数。 */
    public int getDefaultQueryRows() {
        SpiConfig c = spiConfigDao.selectActive(SpiType.RESULT);
        if (c == null || c.getProviderParams() == null) {
            return ResultProperties.DEFAULT_QUERY_ROWS;
        }
        try {
            ResultProperties props = JsonUtils.fromJson(c.getProviderParams(), ResultProperties.class);
            return props.defaultQueryRows();
        } catch (Exception e) {
            return ResultProperties.DEFAULT_QUERY_ROWS;
        }
    }
}
