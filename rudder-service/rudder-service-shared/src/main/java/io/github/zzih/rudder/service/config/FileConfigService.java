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
import io.github.zzih.rudder.dao.dao.FileConfigDao;
import io.github.zzih.rudder.dao.entity.FileConfig;
import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.plugin.FilePluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Map;

import org.springframework.stereotype.Service;

/** FileStorage active 实例的访问入口。 */
@Service
public class FileConfigService extends AbstractConfigService<FileConfig, FileStorage> {

    private final FileConfigDao dao;
    private final FilePluginManager pluginManager;

    public FileConfigService(GlobalCacheService cache, FileConfigDao dao, FilePluginManager pluginManager) {
        super(cache, GlobalCacheKey.FILE, ConfigErrorCode.FILE_NOT_CONFIGURED);
        this.dao = dao;
        this.pluginManager = pluginManager;
    }

    @Override
    protected FileStorage build() {
        FileConfig c = dao.selectActive();
        if (c == null || !Boolean.TRUE.equals(c.getEnabled()) || c.getProvider() == null) {
            return null;
        }
        Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
        return pluginManager.create(c.getProvider(), params);
    }

    @Override
    protected void doUpsert(FileConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
    }

    @Override
    protected HealthStatus healthOf(FileStorage instance) {
        return instance.healthCheck();
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ProviderConfigDTO body) {
        FileConfig c = dao.selectActive();
        if (c == null) {
            c = new FileConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }
}
