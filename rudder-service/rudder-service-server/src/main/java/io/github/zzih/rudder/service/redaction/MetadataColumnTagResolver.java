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

package io.github.zzih.rudder.service.redaction;

import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.metadata.MetadataService;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通过 {@link MetadataService} 查元数据平台拿列上挂的 tag。热路径每行每列都会调,
 * 走 {@link GlobalCacheService}(L1 Caffeine + Redis pub/sub 跨节点 invalidate)。
 *
 * <p>不直接持 {@code MetadataClient} —— 业务层"按 name 解析 datasource → DataSourceInfo"
 * 的装配在 {@link MetadataService} 内,这里复用,避免重复实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataColumnTagResolver implements ColumnTagResolver {

    private final MetadataService metadataService;
    private final GlobalCacheService cache;

    @Override
    public List<String> getTags(String datasourceName, String database, String table, String column) {
        if (datasourceName == null || database == null || table == null || column == null) {
            return List.of();
        }
        String subKey = datasourceName + "|" + database + "|" + table + "|" + column;
        return cache.getOrLoad(GlobalCacheKey.METADATA_TAG, subKey,
                () -> fetchTags(datasourceName, database, table, column));
    }

    private List<String> fetchTags(String datasourceName, String database, String table, String column) {
        try {
            List<ColumnMeta> columns = metadataService.listColumns(datasourceName, null, database, table);
            if (columns == null) {
                return List.of();
            }
            for (ColumnMeta c : columns) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(column)) {
                    return c.getTags() == null ? List.of() : c.getTags();
                }
            }
        } catch (Exception e) {
            log.debug("ColumnTagResolver fetch failed for {}.{}.{}: {}",
                    datasourceName, table, column, e.getMessage());
        }
        return List.of();
    }
}
