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

package io.github.zzih.rudder.metadata.datahub;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.model.ColumnDetail;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * DataHub 元数据 provider。
 *
 * <p>匹配规则:Rudder 数据源名直接当作 DataHub 的
 * {@code platform_instance} 值去做 URN / GraphQL 过滤。
 *
 * <ul>
 *   <li><b>精确模式</b>: 若 DataHub 里存在
 *       {@code urn:li:dataPlatformInstance:(urn:li:dataPlatform:{platform},{dsName})} 实体,则
 *       以此为 URN 前缀和 filter,只返回属于该 instance 的 dataset。</li>
 *   <li><b>降级模式</b>: 若不存在该 instance 实体,则退回"平台级视图",返回该 platform 下
 *       所有 dataset。此时同类型的多个 Rudder 数据源共享同一份 DataHub 元数据。</li>
 * </ul>
 *
 * <p>前置约定:DataHub 侧为每条 ingestion recipe 在 {@code source.config} 下配置
 * {@code platform_instance: <Rudder 数据源名>}。未配置则自动降级。
 */
@Slf4j
public class DatahubMetadataClient implements MetadataClient {

    private static final String PLATFORM_URN_PREFIX = "urn:li:dataPlatform:";
    private static final String PLATFORM_INSTANCE_URN_TEMPLATE =
            "urn:li:dataPlatformInstance:(urn:li:dataPlatform:%s,%s)";
    private static final String DATASET_URN_PREFIX = "urn:li:dataset:";
    private static final String DATASET_URN_TEMPLATE = "urn:li:dataset:(urn:li:dataPlatform:%s,%s,%s)";

    private static final int SEARCH_PAGE_SIZE = 500;
    private static final int SEARCH_MAX_RESULTS = 10000;

    private static final String GQL_SEARCH_DATASETS = """
            query searchDatasets($input: SearchInput!) {
                search(input: $input) {
                    start
                    count
                    total
                    searchResults {
                        entity {
                            urn
                            ... on Dataset {
                                name
                                properties {
                                    name
                                    description
                                }
                                editableProperties {
                                    description
                                }
                            }
                        }
                    }
                }
            }
            """;

    private static final String GQL_SEARCH_DATASET_URNS = """
            query listDatasets($input: SearchInput!) {
                search(input: $input) {
                    total
                    searchResults { entity { urn } }
                }
            }
            """;

    private static final String GQL_GET_INSTANCE = """
            query getInstance($urn: String!) {
                dataPlatformInstance(urn: $urn) { urn }
            }
            """;

    private static final String GQL_GET_SCHEMA = """
            query getSchema($urn: String!) {
                dataset(urn: $urn) {
                    schemaMetadata {
                        fields { fieldPath nativeDataType description }
                    }
                }
            }
            """;

    private static final String GQL_GET_TABLE_DETAIL = """
            query getTableDetail($urn: String!) {
                dataset(urn: $urn) {
                    properties { name description lastModified { time } }
                    editableProperties { description }
                    ownership {
                        owners {
                            owner {
                                ... on CorpUser { username }
                                ... on CorpGroup { name }
                            }
                        }
                    }
                    globalTags { tags { tag { name } } }
                    schemaMetadata {
                        fields { fieldPath nativeDataType description nullable }
                        primaryKeys
                    }
                }
            }
            """;

    private static final String DEFAULT_ENVIRONMENT = "PROD";

    private final DatahubGraphqlClient graphqlClient;
    private final String environment;

    public DatahubMetadataClient(String url, String token, String environment) {
        this.environment = (environment == null || environment.isBlank()) ? DEFAULT_ENVIRONMENT : environment;
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("DataHub metadata: url is required");
        }
        this.graphqlClient = new DatahubGraphqlClient(url, token);
    }

    // ==================== Matching context ====================

    /**
     * 解析后的 DataHub 查询上下文。{@code instanceId == null} 表示降级到平台级视图。
     */
    private record MatchContext(String platform, String instanceId, boolean hasCatalog) {

        boolean usesInstance() {
            return instanceId != null;
        }

        /** URN dataset name 的前缀:非空段用 "." 连接,末尾带 "."。 */
        String prefix(String... segs) {
            String joined = Stream.concat(Stream.of(instanceId), Stream.of(segs))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            return joined.isEmpty() ? "" : joined + ".";
        }

        /** 构造完整 dataset URN name 段(含 table)。 */
        String datasetName(String catalog, String database, String table) {
            return Stream.of(instanceId, catalog, database, table)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
        }

        /** 去除 URN dataset name 里的 instance 前缀段,返回剩余段。 */
        String[] stripInstance(String datasetName) {
            String[] segs = datasetName.split("\\.");
            return usesInstance() && segs.length > 0 ? Arrays.copyOfRange(segs, 1, segs.length) : segs;
        }
    }

    private MatchContext resolveContext(DataSourceInfo target) {
        String platform = target.getType().toLowerCase();

        String instanceId = instanceExists(platform, target.getName()) ? target.getName() : null;
        if (instanceId == null) {
            log.warn("DataHub 未找到 platform_instance={} (platform={}),降级到平台级视图。"
                    + "在 DataHub recipe 的 source.config 下配置 platform_instance: {} 以启用精确匹配。",
                    target.getName(), platform, target.getName());
        }
        return new MatchContext(platform, instanceId, target.isHasCatalog());
    }

    private boolean instanceExists(String platform, String instanceId) {
        String urn = String.format(PLATFORM_INSTANCE_URN_TEMPLATE, platform, instanceId);
        try {
            JsonNode data = graphqlClient.execute(GQL_GET_INSTANCE, Map.of("urn", urn));
            return !data.path("dataPlatformInstance").isMissingNode()
                    && !data.path("dataPlatformInstance").isNull();
        } catch (Exception e) {
            log.debug("Instance existence check failed for {}: {}", urn, e.getMessage());
            return false;
        }
    }

    // ==================== MetadataClient Implementation ====================

    @Override
    public List<String> listCatalogs(DataSourceInfo target) {
        MatchContext ctx = resolveContext(target);
        if (!ctx.hasCatalog()) {
            return List.of();
        }
        Set<String> catalogs = collectFirstSegmentsAfterInstance(ctx);
        return new ArrayList<>(catalogs);
    }

    @Override
    public List<String> listDatabases(DataSourceInfo target, String catalog) {
        MatchContext ctx = resolveContext(target);
        int dbSegIndex = (ctx.hasCatalog() ? 1 : 0);

        Set<String> databases = new LinkedHashSet<>();
        paginatedSearch(GQL_SEARCH_DATASET_URNS, "DATASET", "*", ctx, results -> {
            for (JsonNode r : results) {
                String datasetName = extractDatasetName(r.path("entity").path("urn").asText(""));
                if (datasetName == null) {
                    continue;
                }
                String[] rest = ctx.stripInstance(datasetName);
                if (ctx.hasCatalog()) {
                    if (rest.length < 3 || !rest[0].equals(catalog)) {
                        continue;
                    }
                } else {
                    if (rest.length < 2) {
                        continue;
                    }
                }
                databases.add(rest[dbSegIndex]);
            }
        });
        return new ArrayList<>(databases);
    }

    @Override
    public List<TableMeta> listTables(DataSourceInfo target, String catalog, String database) {
        MatchContext ctx = resolveContext(target);
        String prefix = ctx.prefix(ctx.hasCatalog() ? catalog : null, database);
        List<TableMeta> tables = new ArrayList<>();

        paginatedSearch(GQL_SEARCH_DATASETS, "DATASET", prefix, ctx, results -> {
            for (JsonNode r : results) {
                JsonNode entity = r.path("entity");
                String datasetName = extractDatasetName(entity.path("urn").asText(""));
                if (datasetName == null || !datasetName.startsWith(prefix)) {
                    continue;
                }
                TableMeta meta = new TableMeta();
                meta.setCatalog(catalog);
                meta.setDatabase(database);
                meta.setName(datasetName.substring(prefix.length()));
                meta.setComment(resolveDescription(entity));
                tables.add(meta);
            }
        });
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(DataSourceInfo target, String catalog, String database, String table) {
        MatchContext ctx = resolveContext(target);
        String urn = buildDatasetUrn(ctx, catalog, database, table);
        JsonNode data = graphqlClient.execute(GQL_GET_SCHEMA, Map.of("urn", urn));
        List<ColumnMeta> columns = new ArrayList<>();
        for (JsonNode field : data.path("dataset").path("schemaMetadata").path("fields")) {
            ColumnMeta meta = new ColumnMeta();
            meta.setName(field.path("fieldPath").asText());
            meta.setType(field.path("nativeDataType").asText());
            meta.setComment(field.path("description").asText(null));
            columns.add(meta);
        }
        return columns;
    }

    @Override
    public TableDetail getTableDetail(DataSourceInfo target, String catalog, String database, String table) {
        MatchContext ctx = resolveContext(target);
        String urn = buildDatasetUrn(ctx, catalog, database, table);
        JsonNode data = graphqlClient.execute(GQL_GET_TABLE_DETAIL, Map.of("urn", urn));
        JsonNode dataset = data.path("dataset");

        TableDetail detail = new TableDetail();
        detail.setDatasourceName(target.getName());
        detail.setCatalog(catalog);
        detail.setDatabase(database);
        detail.setTableName(table);
        detail.setDescription(resolveDescription(dataset));

        JsonNode owners = dataset.path("ownership").path("owners");
        if (owners.isArray() && !owners.isEmpty()) {
            JsonNode firstOwner = owners.get(0).path("owner");
            String owner = firstOwner.path("username").asText(null);
            if (owner == null) {
                owner = firstOwner.path("name").asText(null);
            }
            detail.setOwner(owner);
        }

        JsonNode tagsNode = dataset.path("globalTags").path("tags");
        if (tagsNode.isArray() && !tagsNode.isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode t : tagsNode) {
                tags.add(t.path("tag").path("name").asText());
            }
            detail.setTags(tags);
        }

        long lastModifiedMs = dataset.path("properties").path("lastModified").path("time").asLong(0);
        if (lastModifiedMs > 0) {
            detail.setLastModified(LocalDateTime.ofEpochSecond(
                    lastModifiedMs / 1000, 0, ZoneOffset.UTC));
        }

        Set<String> primaryKeys = new LinkedHashSet<>();
        JsonNode pkNode = dataset.path("schemaMetadata").path("primaryKeys");
        if (pkNode.isArray()) {
            for (JsonNode pk : pkNode) {
                primaryKeys.add(pk.asText());
            }
        }

        List<ColumnDetail> columns = new ArrayList<>();
        for (JsonNode field : dataset.path("schemaMetadata").path("fields")) {
            ColumnDetail col = new ColumnDetail();
            String fieldPath = field.path("fieldPath").asText();
            col.setName(fieldPath);
            col.setType(field.path("nativeDataType").asText());
            col.setDescription(field.path("description").asText(null));
            col.setNullable(field.path("nullable").asBoolean(true));
            col.setPrimaryKey(primaryKeys.contains(fieldPath));
            columns.add(col);
        }
        detail.setColumns(columns);
        return detail;
    }

    @Override
    public List<TableMeta> search(DataSourceInfo target, String keyword) {
        MatchContext ctx = resolveContext(target);
        JsonNode data = graphqlClient.execute(GQL_SEARCH_DATASETS,
                buildSearchVariables("DATASET", keyword, ctx, 0, 200));
        List<TableMeta> tables = new ArrayList<>();
        for (JsonNode r : data.path("search").path("searchResults")) {
            JsonNode entity = r.path("entity");
            String datasetName = extractDatasetName(entity.path("urn").asText(""));
            if (datasetName == null) {
                continue;
            }
            TableMeta meta = new TableMeta();
            String[] rest = ctx.stripInstance(datasetName);
            meta.setName(String.join(".", rest));
            meta.setComment(resolveDescription(entity));
            tables.add(meta);
        }
        return tables;
    }

    // ==================== Helpers ====================

    private Set<String> collectFirstSegmentsAfterInstance(MatchContext ctx) {
        Set<String> out = new LinkedHashSet<>();
        paginatedSearch(GQL_SEARCH_DATASET_URNS, "DATASET", "*", ctx, results -> {
            for (JsonNode r : results) {
                String datasetName = extractDatasetName(r.path("entity").path("urn").asText(""));
                if (datasetName == null) {
                    continue;
                }
                String[] rest = ctx.stripInstance(datasetName);
                if (rest.length >= 2) {
                    out.add(rest[0]);
                }
            }
        });
        return out;
    }

    private String buildDatasetUrn(MatchContext ctx, String catalog, String database, String table) {
        String name = ctx.datasetName(ctx.hasCatalog() ? catalog : null, database, table);
        return String.format(DATASET_URN_TEMPLATE, ctx.platform(), name, environment);
    }

    private Map<String, Object> buildSearchVariables(String type, String query, MatchContext ctx,
                                                     int start, int count) {
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(Map.of("field", "platform",
                "values", List.of(PLATFORM_URN_PREFIX + ctx.platform())));
        if (ctx.usesInstance()) {
            filters.add(Map.of("field", "platformInstance",
                    "values", List.of(String.format(PLATFORM_INSTANCE_URN_TEMPLATE,
                            ctx.platform(), ctx.instanceId()))));
        }
        return Map.of("input", Map.of(
                "type", type,
                "query", query,
                "filters", filters,
                "start", start,
                "count", count));
    }

    private void paginatedSearch(String gql, String type, String query, MatchContext ctx,
                                 java.util.function.Consumer<JsonNode> pageConsumer) {
        int start = 0;
        while (start < SEARCH_MAX_RESULTS) {
            JsonNode data = graphqlClient.execute(gql,
                    buildSearchVariables(type, query, ctx, start, SEARCH_PAGE_SIZE));
            JsonNode searchNode = data.path("search");
            JsonNode results = searchNode.path("searchResults");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            pageConsumer.accept(results);

            int total = searchNode.path("total").asInt(-1);
            start += SEARCH_PAGE_SIZE;
            if (total <= 0 || start >= total) {
                break;
            }
        }
        if (start >= SEARCH_MAX_RESULTS) {
            log.warn("DataHub search reached max result limit ({}) for type={} query='{}' platform={} instance={}",
                    SEARCH_MAX_RESULTS, type, query, ctx.platform(), ctx.instanceId());
        }
    }

    private String resolveDescription(JsonNode entity) {
        String desc = entity.path("editableProperties").path("description").asText(null);
        if (desc == null) {
            desc = entity.path("properties").path("description").asText(null);
        }
        return desc;
    }

    /** 从 {@code urn:li:dataset:(urn:li:dataPlatform:X, A.B.C, ENV)} 中取出 {@code A.B.C}。 */
    private String extractDatasetName(String urn) {
        if (urn == null || !urn.startsWith(DATASET_URN_PREFIX)) {
            return null;
        }
        int openParen = urn.indexOf('(');
        int closeParen = urn.lastIndexOf(')');
        if (openParen < 0 || closeParen < 0) {
            return null;
        }
        String tuple = urn.substring(openParen + 1, closeParen);
        int firstComma = tuple.indexOf(',');
        int lastComma = tuple.lastIndexOf(',');
        if (firstComma < 0 || lastComma <= firstComma) {
            return null;
        }
        return tuple.substring(firstComma + 1, lastComma);
    }
}
