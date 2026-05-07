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

package io.github.zzih.rudder.service.metadata;

import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.service.config.MetadataConfigService;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.coordination.cache.MetadataCacheKeys;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;

/**
 * 元数据查询入口。cache-aside:hit 直接返,miss 调 active provider 查源并回写。SPI provider
 * 不接触缓存,所有 datasource → DataSourceInfo 的装配由 {@link DatasourceService} 单点完成。
 */
@Service
@RequiredArgsConstructor
public class MetadataService {

    private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {
    };
    private static final TypeReference<List<TableMeta>> LIST_TABLE_META = new TypeReference<>() {
    };
    private static final TypeReference<List<ColumnMeta>> LIST_COLUMN_META = new TypeReference<>() {
    };

    private final MetadataConfigService configService;
    private final DatasourceService datasourceService;
    private final GlobalCacheService cache;

    public List<String> listCatalogs(String datasourceName) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.catalogs(datasourceName), LIST_STRING,
                () -> configService.required().listCatalogs(datasourceService.getDataSourceInfoByName(datasourceName)));
    }

    public List<String> listDatabases(String datasourceName, String catalog) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.databases(datasourceName, catalog), LIST_STRING,
                () -> configService.required().listDatabases(datasourceService.getDataSourceInfoByName(datasourceName),
                        catalog));
    }

    public List<TableMeta> listTables(String datasourceName, String catalog, String database) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.tables(datasourceName, catalog, database), LIST_TABLE_META,
                () -> configService.required().listTables(datasourceService.getDataSourceInfoByName(datasourceName),
                        catalog, database));
    }

    public List<ColumnMeta> listColumns(String datasourceName, String catalog, String database, String table) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.columns(datasourceName, catalog, database, table), LIST_COLUMN_META,
                () -> configService.required().listColumns(datasourceService.getDataSourceInfoByName(datasourceName),
                        catalog, database, table));
    }

    public TableDetail getTableDetail(String datasourceName, String catalog, String database, String table) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.tableDetail(datasourceName, catalog, database, table), TableDetail.class,
                () -> configService.required().getTableDetail(datasourceService.getDataSourceInfoByName(datasourceName),
                        catalog, database,
                        table));
    }

    public List<TableMeta> search(String datasourceName, String keyword) {
        return cache.getOrLoad(GlobalCacheKey.METADATA_DATA,
                MetadataCacheKeys.search(datasourceName, keyword), LIST_TABLE_META,
                () -> configService.required().search(datasourceService.getDataSourceInfoByName(datasourceName),
                        keyword));
    }

    /** 失效该数据源全部缓存。元数据同步、管理员"刷新"按钮调用。 */
    public void invalidateByDatasource(String datasourceName) {
        cache.invalidateByPrefix(GlobalCacheKey.METADATA_DATA, datasourceName + ":");
    }
}
