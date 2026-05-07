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

package io.github.zzih.rudder.metadata.openmetadata;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.model.ColumnDetail;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenMetadata provider。
 *
 * <p>匹配规则:Rudder 数据源名必须等于 OpenMetadata
 * 里对应 <b>Database Service</b> 的 name。
 *
 * <p>OpenMetadata 的实体层级固定为 4 层:
 * <pre>
 *   DatabaseService  ── 物理部署(= Rudder 数据源,用 name 匹配)
 *     └─ Database     ── 两层引擎时 = 用户数据库; 三层引擎时 = catalog
 *         └─ DatabaseSchema ── 两层引擎时通常是 "default"; 三层时 = schema/database
 *             └─ Table
 * </pre>
 *
 * <p>映射到 Rudder SPI 的 (catalog, database, table) 参数:
 * <ul>
 *   <li><b>2 层 (Hive / MySQL / Spark / Flink)</b>:catalog=null,
 *       SPI.database ↔ OM.Database,SPI.table ↔ OM.Table (默认 schema 自动定位)。</li>
 *   <li><b>3 层 (Trino / StarRocks)</b>:SPI.catalog ↔ OM.Database,
 *       SPI.database ↔ OM.DatabaseSchema,SPI.table ↔ OM.Table。</li>
 * </ul>
 *
 * <p>纯查询实现,不持任何缓存。查询结果缓存由宿主侧统一做 cache-aside。
 */
@Slf4j
public class OpenMetadataClient implements MetadataClient {

    private static final int PAGE_SIZE = 100;

    private final OpenMetadataRestClient rest;

    public OpenMetadataClient(String url, String token) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("OpenMetadata: url is required");
        }
        this.rest = new OpenMetadataRestClient(url, token);
    }

    // ==================== Matching context ====================

    private record ServiceContext(String serviceName, boolean hasCatalog) {
    }

    private ServiceContext resolveContext(DataSourceInfo target) {
        return new ServiceContext(target.getName(), target.isHasCatalog());
    }

    // ==================== MetadataClient ====================

    @Override
    public List<String> listCatalogs(DataSourceInfo target) {
        ServiceContext ctx = resolveContext(target);
        if (!ctx.hasCatalog()) {
            return List.of();
        }
        // 3 层模式:Database 层就是 catalog
        return listOmDatabases(ctx.serviceName());
    }

    @Override
    public List<String> listDatabases(DataSourceInfo target, String catalog) {
        ServiceContext ctx = resolveContext(target);
        if (ctx.hasCatalog()) {
            // 3 层:list schemas under {service}.{catalog}
            return listOmSchemas(ctx.serviceName() + "." + catalog);
        }
        // 2 层:list OM Databases under service
        return listOmDatabases(ctx.serviceName());
    }

    @Override
    public List<TableMeta> listTables(DataSourceInfo target, String catalog, String database) {
        ServiceContext ctx = resolveContext(target);
        String schemaFqn = resolveSchemaFqn(ctx, catalog, database);
        if (schemaFqn == null) {
            return List.of();
        }

        List<TableMeta> tables = new ArrayList<>();
        paginate("/tables?databaseSchema=" + OpenMetadataRestClient.encode(schemaFqn), entry -> {
            TableMeta meta = new TableMeta();
            meta.setCatalog(catalog);
            meta.setDatabase(database);
            meta.setName(entry.path("name").asText());
            meta.setComment(entry.path("description").asText(null));
            tables.add(meta);
        });
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(DataSourceInfo target, String catalog, String database, String table) {
        ServiceContext ctx = resolveContext(target);
        JsonNode tableNode = getTableByFqn(ctx, catalog, database, table, "columns");
        List<ColumnMeta> columns = new ArrayList<>();
        if (tableNode != null) {
            for (JsonNode col : tableNode.path("columns")) {
                ColumnMeta meta = new ColumnMeta();
                meta.setName(col.path("name").asText());
                meta.setType(col.path("dataTypeDisplay").asText(col.path("dataType").asText()));
                meta.setComment(col.path("description").asText(null));
                columns.add(meta);
            }
        }
        return columns;
    }

    @Override
    public TableDetail getTableDetail(DataSourceInfo target, String catalog, String database, String table) {
        ServiceContext ctx = resolveContext(target);
        JsonNode node = getTableByFqn(ctx, catalog, database, table,
                "columns,owner,tags,description");
        if (node == null) {
            throw new io.github.zzih.rudder.common.exception.BizException(
                    io.github.zzih.rudder.common.enums.error.SpiErrorCode.PROVIDER_EXECUTION_FAILED,
                    "OpenMetadata table not found: "
                            + ctx.serviceName() + "/" + catalog + "/" + database + "/" + table);
        }

        TableDetail detail = new TableDetail();
        detail.setDatasourceName(target.getName());
        detail.setCatalog(catalog);
        detail.setDatabase(database);
        detail.setTableName(table);
        detail.setDescription(node.path("description").asText(null));

        JsonNode owner = node.path("owner");
        if (owner.isObject() && !owner.isEmpty()) {
            String name = owner.path("displayName").asText(null);
            if (name == null) {
                name = owner.path("name").asText(null);
            }
            detail.setOwner(name);
        }

        JsonNode tagsArr = node.path("tags");
        if (tagsArr.isArray() && !tagsArr.isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode t : tagsArr) {
                tags.add(t.path("tagFQN").asText());
            }
            detail.setTags(tags);
        }

        long updatedMs = node.path("updatedAt").asLong(0);
        if (updatedMs > 0) {
            detail.setLastModified(LocalDateTime.ofEpochSecond(updatedMs / 1000, 0, ZoneOffset.UTC));
        }

        Set<String> primaryKeys = collectConstraintColumns(node, "PRIMARY_KEY");

        List<ColumnDetail> columns = new ArrayList<>();
        for (JsonNode col : node.path("columns")) {
            ColumnDetail cd = new ColumnDetail();
            String name = col.path("name").asText();
            cd.setName(name);
            cd.setType(col.path("dataTypeDisplay").asText(col.path("dataType").asText()));
            cd.setDescription(col.path("description").asText(null));
            // OM 的 constraint 字段值可能是 "NULL"/"NOT_NULL"/"UNIQUE"/"PRIMARY_KEY"
            String constraint = col.path("constraint").asText("");
            cd.setNullable(!"NOT_NULL".equals(constraint) && !"PRIMARY_KEY".equals(constraint));
            cd.setPrimaryKey(primaryKeys.contains(name) || "PRIMARY_KEY".equals(constraint));
            columns.add(cd);
        }
        detail.setColumns(columns);
        return detail;
    }

    @Override
    public List<TableMeta> search(DataSourceInfo target, String keyword) {
        ServiceContext ctx = resolveContext(target);
        // OpenMetadata Elasticsearch 代理搜索端点
        // 用 service 字段过滤,确保只返回本 service 下的表
        String path = "/search/query?q=" + OpenMetadataRestClient.encode(keyword)
                + "%20AND%20service.name:" + OpenMetadataRestClient.encode(ctx.serviceName())
                + "&index=table_search_index&from=0&size=100";
        JsonNode res = rest.get(path);
        List<TableMeta> tables = new ArrayList<>();
        if (res != null) {
            for (JsonNode hit : res.path("hits").path("hits")) {
                JsonNode src = hit.path("_source");
                TableMeta m = new TableMeta();
                // FQN 形如 service.db.schema.table,去掉前两段留下业务层级
                String fqn = src.path("fullyQualifiedName").asText("");
                m.setName(fqn);
                m.setComment(src.path("description").asText(null));
                tables.add(m);
            }
        }
        return tables;
    }

    // ==================== Helpers ====================

    private List<String> listOmDatabases(String serviceName) {
        Set<String> out = new LinkedHashSet<>();
        paginate("/databases?service=" + OpenMetadataRestClient.encode(serviceName),
                entry -> out.add(entry.path("name").asText()));
        return new ArrayList<>(out);
    }

    private List<String> listOmSchemas(String databaseFqn) {
        Set<String> out = new LinkedHashSet<>();
        paginate("/databaseSchemas?database=" + OpenMetadataRestClient.encode(databaseFqn),
                entry -> out.add(entry.path("name").asText()));
        return new ArrayList<>(out);
    }

    /**
     * OpenMetadata cursor 分页循环。{@code pathPrefix} 含已编码的 filter,但不含 limit/after。
     */
    private void paginate(String pathPrefix, java.util.function.Consumer<JsonNode> entryConsumer) {
        String after = null;
        do {
            String path = pathPrefix + "&limit=" + PAGE_SIZE
                    + (after == null ? "" : "&after=" + OpenMetadataRestClient.encode(after));
            JsonNode page = rest.get(path);
            if (page == null) {
                break;
            }
            for (JsonNode entry : page.path("data")) {
                entryConsumer.accept(entry);
            }
            after = page.path("paging").path("after").asText(null);
        } while (after != null && !after.isBlank());
    }

    /**
     * 把 Rudder 的 (catalog, database) 映射到 OpenMetadata 的 DatabaseSchema FQN。
     *
     * <ul>
     *   <li>3 层: {service}.{catalog}.{database}  — SPI.database 对应 OM schema</li>
     *   <li>2 层: {service}.{database}.&lt;firstSchema&gt; — OM schema 由列表首个决定</li>
     * </ul>
     */
    private String resolveSchemaFqn(ServiceContext ctx, String catalog, String database) {
        if (ctx.hasCatalog()) {
            return ctx.serviceName() + "." + catalog + "." + database;
        }
        // 2 层:每次分页列 schema 取首个。上层 MetadataService 已缓存查询结果,这里被调到的频率本就低。
        String databaseFqn = ctx.serviceName() + "." + database;
        List<String> schemas = listOmSchemas(databaseFqn);
        if (schemas.isEmpty()) {
            log.warn("No DatabaseSchema found under {} in OpenMetadata", databaseFqn);
            return null;
        }
        return databaseFqn + "." + schemas.get(0);
    }

    /** 拉取具体某张表,fields 控制返回哪些字段(OpenMetadata API 规范)。 */
    private JsonNode getTableByFqn(ServiceContext ctx, String catalog, String database, String table,
                                   String fields) {
        String schemaFqn = resolveSchemaFqn(ctx, catalog, database);
        if (schemaFqn == null) {
            return null;
        }
        String tableFqn = schemaFqn + "." + table;
        String path = "/tables/name/" + OpenMetadataRestClient.encode(tableFqn)
                + "?fields=" + fields;
        return rest.get(path);
    }

    private Set<String> collectConstraintColumns(JsonNode table, String constraintType) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode tc : table.path("tableConstraints")) {
            if (constraintType.equals(tc.path("constraintType").asText(""))) {
                for (JsonNode col : tc.path("columns")) {
                    out.add(col.asText());
                }
            }
        }
        return out;
    }
}
