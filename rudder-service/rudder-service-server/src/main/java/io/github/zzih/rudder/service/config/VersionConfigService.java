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
import io.github.zzih.rudder.dao.dao.VersionConfigDao;
import io.github.zzih.rudder.dao.entity.VersionConfig;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.plugin.VersionPluginManager;

import java.util.Map;

import org.springframework.stereotype.Service;

/** VersionStore active 实例的访问入口。 */
@Service
public class VersionConfigService extends AbstractConfigService<VersionConfig, VersionStore> {

    private final VersionConfigDao dao;
    private final VersionPluginManager pluginManager;
    /** 与 {@link #active()} 同生命周期,避免每次写版本都额外查一次 dao。 */
    private volatile String activeProvider;

    public VersionConfigService(GlobalCacheService cache, VersionConfigDao dao, VersionPluginManager pluginManager) {
        super(cache, GlobalCacheKey.VERSION, ConfigErrorCode.VERSION_NOT_CONFIGURED);
        this.dao = dao;
        this.pluginManager = pluginManager;
    }

    /** 当前 active provider 名(LOCAL / GIT)。未配置时返回 null。 */
    public String activeProvider() {
        active();
        return activeProvider;
    }

    @Override
    protected VersionStore build() {
        VersionConfig c = dao.selectActive();
        if (c == null || !Boolean.TRUE.equals(c.getEnabled()) || c.getProvider() == null) {
            activeProvider = null;
            return null;
        }
        activeProvider = c.getProvider();
        Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
        return pluginManager.create(c.getProvider(), params);
    }

    @Override
    protected void doUpsert(VersionConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
    }

    @Override
    protected HealthStatus healthOf(VersionStore instance) {
        return instance.healthCheck();
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ProviderConfigDTO body) {
        VersionConfig c = dao.selectActive();
        if (c == null) {
            c = new VersionConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }
}
