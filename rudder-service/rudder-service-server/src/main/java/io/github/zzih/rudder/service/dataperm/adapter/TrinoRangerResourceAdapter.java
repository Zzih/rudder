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

package io.github.zzih.rudder.service.dataperm.adapter;

import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerAdminRestClient;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;

import org.springframework.stereotype.Component;

/**
 * TRINO plugin adapter,对应 Trino Ranger servicedef({@code name = "trino"})。
 * Rudder {@code databaseName} 字段在 Trino 语境映射为 schema。
 */
@Component
public class TrinoRangerResourceAdapter extends AbstractRangerResourceAdapter {

    public TrinoRangerResourceAdapter(GlobalCacheService cache,
                                      RangerAdminRestClient rangerClient,
                                      DataPermConfigService configService) {
        super(cache, rangerClient, configService);
    }

    @Override
    public PluginType supportedPluginType() {
        return PluginType.TRINO;
    }

    @Override
    protected String valueAt(ResourceLevel level, DataPermPermissionItemDTO item) {
        return switch (level) {
            case CATALOG -> item.getCatalogName();
            case SCHEMA -> item.getDatabaseName();
            case TABLE -> item.getTableName();
            case COLUMN -> item.getColumnName();
            default -> null;
        };
    }
}
