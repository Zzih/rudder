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

package io.github.zzih.rudder.ai.knowledge;

import io.github.zzih.rudder.ai.dto.AiMetadataSyncConfigDTO;
import io.github.zzih.rudder.ai.rag.DocumentIngestionService;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiDocumentDao;
import io.github.zzih.rudder.dao.dao.AiMetadataSyncConfigDao;
import io.github.zzih.rudder.dao.entity.AiDocument;
import io.github.zzih.rudder.dao.entity.AiMetadataSyncConfig;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.model.ColumnDetail;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.service.config.MetadataConfigService;
import io.github.zzih.rudder.service.metadata.MetadataService;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 表结构同步服务。每个 datasource 一条 {@link AiMetadataSyncConfig} 配置,
 * 从 MetadataClient 拉库表结构,合成每表一份 markdown 作为 SCHEMA 类 AiDocument。
 * 通过 sourceRef = {@code metadata:{datasource}:{db}.{table}} 做增量 upsert。
 * <p>
 * <b>平台级共享(不按工作空间隔离)</b>:同步产出的 SCHEMA 文档 {@code workspace_id=null},
 * 所有工作空间共用。设计动机:数据源本身是跨工作空间的共用资产,表结构不具隐私属性,按工作空间切分
 * 会导致同一个数据源被重复同步 N 份,维护成本爆炸。如果未来出现"某表只有 A 工作区能看"的合规需求,
 * 应当在数据源访问权限层(datasource_permission)拦截,不要退回把元数据切给工作空间。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataSyncService {

    /**
     * 每库内表级并发度。瓶颈是 getTableDetail (metadata RPC) + upsertBySourceRef (embedding RPC),
     * 两者都是网络等待。8 是个保守值,避免对 LLM embedding 服务造成突发压力,同时把万张表的同步从
     * 数小时压到分钟级。
     */
    private static final int TABLE_PARALLELISM = 8;

    private final AiMetadataSyncConfigDao configDao;
    private final AiDocumentDao documentDao;
    private final DocumentIngestionService documentIngestionService;
    private final DatasourceService datasourceService;
    private final MetadataConfigService metadataConfigService;
    private final MetadataService metadataService;
    private final ObjectMapper objectMapper;

    public List<AiMetadataSyncConfig> list() {
        return configDao.selectAll();
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiMetadataSyncConfig> page(int pageNum, int pageSize) {
        return configDao.selectPage(pageNum, pageSize);
    }

    public AiMetadataSyncConfig getByDatasourceId(Long datasourceId) {
        return configDao.selectByDatasourceId(datasourceId);
    }

    public AiMetadataSyncConfig save(AiMetadataSyncConfig config) {
        if (config.getDatasourceId() == null) {
            throw new IllegalArgumentException("datasourceId required");
        }
        AiMetadataSyncConfig existing = configDao.selectByDatasourceId(config.getDatasourceId());
        if (existing == null) {
            if (config.getEnabled() == null) {
                config.setEnabled(1);
            }
            if (config.getMaxColumnsPerTable() == null) {
                config.setMaxColumnsPerTable(50);
            }
            configDao.insert(config);
            return config;
        }
        existing.setEnabled(config.getEnabled() != null ? config.getEnabled() : existing.getEnabled());
        existing.setScheduleCron(config.getScheduleCron());
        existing.setIncludeCatalogs(config.getIncludeCatalogs());
        existing.setIncludeDatabases(config.getIncludeDatabases());
        existing.setIncludeTables(config.getIncludeTables());
        existing.setExcludeTables(config.getExcludeTables());
        existing.setMaxColumnsPerTable(
                config.getMaxColumnsPerTable() != null ? config.getMaxColumnsPerTable()
                        : existing.getMaxColumnsPerTable());
        existing.setAccessPaths(config.getAccessPaths());
        configDao.updateById(existing);
        return existing;
    }

    /**
     * 删配置时联动清理:同步产出的 SCHEMA 文档 + 向量点 + embedding 元信息。
     * 不联动清会留下一堆没有入口的"僵尸"文档在知识库里污染 AI 检索。
     */
    public void delete(Long id) {
        AiMetadataSyncConfig config = configDao.selectById(id);
        if (config != null && config.getDatasourceId() != null) {
            try {
                Datasource ds = datasourceService.getById(config.getDatasourceId());
                purgeSchemaDocuments(ds.getName());
            } catch (Exception e) {
                // datasource 已经被删除之类的情况,文档清理只能尽力而为
                log.warn("schema doc purge for config {} failed: {}", id, e.getMessage());
            }
        }
        configDao.deleteById(id);
    }

    private void purgeSchemaDocuments(String datasourceName) {
        String refPrefix = "metadata:" + datasourceName + ":";
        List<AiDocument> docs = documentDao.selectBySourceRefPrefix(refPrefix);
        ExecutorService pool = newSyncPool("metadata-purge-" + datasourceName);
        int purged;
        try {
            purged = parallelDeleteDocuments(pool, docs, doc -> true);
        } finally {
            shutdownAndAwait(pool);
        }
        log.info("purged {} SCHEMA documents for datasource {}", purged, datasourceName);
    }

    public List<AiMetadataSyncConfig> listScheduled() {
        return configDao.selectEnabledWithCron();
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public com.baomidou.mybatisplus.core.metadata.IPage<AiMetadataSyncConfigDTO> pageDetail(int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(page(pageNum, pageSize), AiMetadataSyncConfigDTO.class);
    }

    public AiMetadataSyncConfigDTO getByDatasourceIdDetail(Long datasourceId) {
        return BeanConvertUtils.convert(getByDatasourceId(datasourceId), AiMetadataSyncConfigDTO.class);
    }

    public AiMetadataSyncConfigDTO saveDetail(AiMetadataSyncConfigDTO body) {
        AiMetadataSyncConfig entity = BeanConvertUtils.convert(body, AiMetadataSyncConfig.class);
        return BeanConvertUtils.convert(save(entity), AiMetadataSyncConfigDTO.class);
    }

    /** 按 datasourceId 触发一次同步。 */
    public SyncResult syncByDatasourceId(Long datasourceId) {
        AiMetadataSyncConfig config = configDao.selectByDatasourceId(datasourceId);
        if (config == null) {
            return SyncResult.empty("config not found");
        }
        return sync(config);
    }

    /**
     * 执行一次同步。{@code enabled} 字段**只控制调度器是否自动跑**
     * (DB 层已在 {@link #listScheduled()} 过滤),这里不再判断 —— 管理员点"立即同步"
     * 就是要强制跑一次,哪怕 schedule 被关掉。
     */
    public SyncResult sync(AiMetadataSyncConfig config) {
        if (config == null) {
            return SyncResult.empty("config null");
        }
        markRunning(config);
        try {
            SyncResult result = doSync(config);
            markResult(config, "SUCCESS", result.summary());
            log.info("metadata sync done datasource={}: {}", config.getDatasourceId(), result.summary());
            return result;
        } catch (Exception e) {
            log.warn("metadata sync {} failed: {}", config.getDatasourceId(), e.getMessage(), e);
            markResult(config, "FAILED", e.getMessage());
            return SyncResult.empty("sync failed: " + e.getMessage());
        }
    }

    private SyncResult doSync(AiMetadataSyncConfig config) {
        Datasource ds;
        try {
            ds = datasourceService.getById(config.getDatasourceId());
        } catch (Exception e) {
            return SyncResult.empty("datasource not found: " + config.getDatasourceId());
        }
        String engineType = ds.getDatasourceType();
        MetadataClient client = metadataConfigService.active();
        if (client == null) {
            return SyncResult.empty("metadata client not configured");
        }
        // 一次性解析 target,sync 循环里直连 SPI,避免每张表都重做凭证解密(N+1 退化)
        DataSourceInfo target = datasourceService.getDataSourceInfoByName(ds.getName());
        // 清平台元数据缓存,确保接下来读到的 DDL 是最新(不然 TTL 内的改动检测不到)
        try {
            metadataService.invalidateByDatasource(ds.getName());
        } catch (Exception e) {
            log.debug("invalidate metadata cache skipped for {}: {}", ds.getName(), e.getMessage());
        }

        Set<String> includeCatalogs = readStringSet(config.getIncludeCatalogs());
        Set<String> includeDbs = readStringSet(config.getIncludeDatabases());
        Set<String> includeTables = readStringSet(config.getIncludeTables());
        List<String> excludeKeywords = readExcludeKeywords(config.getExcludeTables());
        int maxCols = config.getMaxColumnsPerTable() != null ? config.getMaxColumnsPerTable() : 50;
        List<AccessPath> accessPaths = readAccessPaths(config.getAccessPaths());
        String refPrefix = "metadata:" + ds.getName() + ":";

        // 三层引擎遍历所有 catalog;两层引擎只跑一次 catalog=null
        List<String> catalogs;
        try {
            catalogs = client.listCatalogs(target);
        } catch (Exception e) {
            log.warn("listCatalogs {} failed: {}", ds.getName(), e.getMessage());
            catalogs = List.of();
        }
        boolean hasCatalog = !catalogs.isEmpty();
        if (catalogs.isEmpty()) {
            catalogs = java.util.Collections.singletonList(null);
        }
        if (hasCatalog && !includeCatalogs.isEmpty()) {
            catalogs = catalogs.stream().filter(includeCatalogs::contains).toList();
        }
        log.info("metadata sync datasource={} catalogs={}", ds.getName(), catalogs);

        AtomicInteger ins = new AtomicInteger();
        AtomicInteger upd = new AtomicInteger();
        AtomicInteger skp = new AtomicInteger();
        Set<String> seenRefs = ConcurrentHashMap.newKeySet();
        int deleted = 0;

        ExecutorService pool = newSyncPool("metadata-sync-" + ds.getName());
        try {
            for (String catalog : catalogs) {
                List<String> dbs;
                try {
                    dbs = client.listDatabases(target, catalog);
                } catch (Exception e) {
                    log.warn("listDatabases {}:{} failed: {}", ds.getName(), catalog, e.getMessage());
                    continue;
                }
                if (!includeDbs.isEmpty()) {
                    dbs = dbs.stream()
                            .filter(db -> includeDbs.contains(qualify(catalog, db)))
                            .toList();
                }
                for (String db : dbs) {
                    List<TableMeta> tables;
                    try {
                        tables = client.listTables(target, catalog, db);
                    } catch (Exception e) {
                        log.warn("listTables {}:{}.{} failed: {}", ds.getName(), catalog, db, e.getMessage());
                        continue;
                    }
                    List<java.util.concurrent.Future<?>> futures = new ArrayList<>(tables.size());
                    for (TableMeta t : tables) {
                        String tableName = t.getName();
                        if (tableName == null) {
                            continue;
                        }
                        if (!includeTables.isEmpty()
                                && !includeTables.contains(qualify(catalog, db, tableName))) {
                            continue;
                        }
                        if (containsAnyKeyword(tableName, excludeKeywords)) {
                            continue;
                        }
                        final String fCatalog = catalog;
                        final String fDb = db;
                        final TableMeta fTable = t;
                        futures.add(pool.submit(() -> syncOneTable(client, target, ds, engineType,
                                fCatalog, fDb, fTable, maxCols, accessPaths, refPrefix,
                                ins, upd, skp, seenRefs)));
                    }
                    awaitAll(futures);
                }
            }
            deleted = cleanupRemoved(pool, refPrefix, seenRefs);
        } finally {
            shutdownAndAwait(pool);
        }
        // 一致性 sweep:清掉孤儿 embedding(AiDocument 已删但 Qdrant 点还在),保证向量库和 MySQL 对齐
        try {
            int sweep = documentIngestionService.sweepOrphanEmbeddings(2000);
            if (sweep > 0) {
                log.info("metadata sync {} swept {} orphan embeddings", ds.getName(), sweep);
            }
        } catch (Exception e) {
            log.warn("metadata sync {} sweep failed: {}", ds.getName(), e.getMessage());
        }
        return new SyncResult(ins.get(), upd.get(), skp.get(), deleted, null);
    }

    private void syncOneTable(MetadataClient client, DataSourceInfo target, Datasource ds, String engineType,
                              String catalog, String db, TableMeta t, int maxCols, List<AccessPath> accessPaths,
                              String refPrefix, AtomicInteger ins, AtomicInteger upd, AtomicInteger skp,
                              Set<String> seenRefs) {
        String tableName = t.getName();
        // 用 getTableDetail 而不是 listColumns:能拿到 nullable / primaryKey 等更多维度,
        // DDL 只改 NULL/NOT NULL / PK 这类字段也能触发 markdown hash 变 → UPDATE
        List<ColumnDetail> cols;
        String tableComment = t.getComment();
        try {
            TableDetail detail = client.getTableDetail(target, catalog, db, tableName);
            cols = detail.getColumns() == null ? List.of() : detail.getColumns();
            if (detail.getDescription() != null && !detail.getDescription().isBlank()) {
                tableComment = detail.getDescription();
            }
        } catch (Exception e) {
            log.warn("getTableDetail {}:{}.{}.{} failed: {}",
                    ds.getName(), catalog, db, tableName, e.getMessage());
            return;
        }
        if (cols.size() > maxCols) {
            cols = cols.subList(0, maxCols);
        }
        String qualifiedPath = catalog == null ? db + "." + tableName
                : catalog + "." + db + "." + tableName;
        String markdown = buildMarkdown(ds, db, tableName, tableComment, cols, engineType, accessPaths);
        String ref = refPrefix + qualifiedPath;
        seenRefs.add(ref);

        AiDocument doc = new AiDocument();
        doc.setDocType("SCHEMA");
        doc.setEngineType(engineType);
        doc.setSourceRef(ref);
        doc.setTitle(qualifiedPath);
        doc.setContent(markdown);
        try {
            switch (documentIngestionService.upsertBySourceRef(doc)) {
                case INSERTED -> ins.incrementAndGet();
                case UPDATED -> upd.incrementAndGet();
                case SKIPPED -> skp.incrementAndGet();
            }
        } catch (Exception e) {
            log.warn("upsert {} failed: {}", ref, e.getMessage());
        }
    }

    private void awaitAll(List<java.util.concurrent.Future<?>> futures) {
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.util.concurrent.ExecutionException ee) {
                // syncOneTable 内已 catch + log,这里到不了;兜底防止意外抛出阻塞整库
                log.warn("table sync future failed: {}", ee.getCause() != null
                        ? ee.getCause().getMessage()
                        : ee.getMessage());
            }
        }
    }

    /**
     * 删掉当前 sync 范围内已经不存在的老文档。走 {@link DocumentIngestionService#delete}
     * 才能联动清理 vector + embedding,直连 documentDao 会让 vector 成孤儿池。
     */
    private int cleanupRemoved(ExecutorService pool, String refPrefix, Set<String> seen) {
        List<AiDocument> docs = documentDao.selectBySourceRefPrefix(refPrefix);
        return parallelDeleteDocuments(pool, docs,
                doc -> doc.getSourceRef() != null && !seen.contains(doc.getSourceRef()));
    }

    private int parallelDeleteDocuments(ExecutorService pool, List<AiDocument> docs,
                                        java.util.function.Predicate<AiDocument> filter) {
        AtomicInteger count = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (AiDocument doc : docs) {
            if (!filter.test(doc)) {
                continue;
            }
            futures.add(pool.submit(() -> {
                try {
                    documentIngestionService.delete(doc.getId());
                    count.incrementAndGet();
                } catch (Exception e) {
                    log.warn("delete {} failed: {}",
                            doc.getSourceRef() != null ? doc.getSourceRef() : doc.getId(), e.getMessage());
                }
            }));
        }
        awaitAll(futures);
        return count.get();
    }

    private ExecutorService newSyncPool(String name) {
        return Executors.newFixedThreadPool(TABLE_PARALLELISM, r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    private void shutdownAndAwait(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("metadata sync pool did not terminate in 5 min, forcing shutdown");
                pool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    private String buildMarkdown(Datasource ds, String db, String table, String tableComment,
                                 List<ColumnDetail> cols, String engineType, List<AccessPath> accessPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(db).append('.').append(table)
                .append("  (engine: ").append(engineType).append(")\n\n");
        if (tableComment != null && !tableComment.isBlank()) {
            sb.append(tableComment).append("\n\n");
        }
        sb.append("## Columns\n\n");
        sb.append("| Column | Type | Nullable | PK | Comment |\n");
        sb.append("|--------|------|----------|----|---------|\n");
        for (ColumnDetail c : cols) {
            sb.append("| ").append(nullSafe(c.getName()))
                    .append(" | ").append(nullSafe(c.getType()))
                    .append(" | ").append(c.isNullable() ? "Y" : "N")
                    .append(" | ").append(c.isPrimaryKey() ? "Y" : "")
                    .append(" | ").append(nullSafe(c.getDescription()).replace("|", "\\|"))
                    .append(" |\n");
        }
        sb.append("\n## Access Paths\n\n");
        sb.append("- ").append(engineType).append(": `").append(db).append('.').append(table).append("`\n");
        for (AccessPath ap : accessPaths) {
            String path = ap.template().replace("{db}", db).replace("{table}", table);
            sb.append("- ").append(ap.engine()).append(": `").append(path).append("`\n");
        }
        sb.append("\n_Primary datasource: ").append(ds.getName()).append(" (").append(engineType).append(")_\n");
        return sb.toString();
    }

    private void markRunning(AiMetadataSyncConfig c) {
        c.setLastSyncStatus("RUNNING");
        c.setLastSyncAt(LocalDateTime.now());
        configDao.updateById(c);
    }

    private void markResult(AiMetadataSyncConfig c, String status, String message) {
        c.setLastSyncStatus(status);
        c.setLastSyncAt(LocalDateTime.now());
        if (message != null && message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        c.setLastSyncMessage(message);
        configDao.updateById(c);
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }

    private Set<String> readStringSet(String json) {
        Set<String> out = new HashSet<>();
        JsonNode n = parseJson(json);
        if (n.isArray()) {
            n.forEach(v -> {
                String s = v.asText("");
                if (!s.isBlank()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    private List<AccessPath> readAccessPaths(String json) {
        List<AccessPath> out = new ArrayList<>();
        JsonNode n = parseJson(json);
        if (n.isArray()) {
            for (JsonNode item : n) {
                String eng = item.path("engine").asText("");
                String tpl = item.path("template").asText("");
                if (!eng.isBlank() && !tpl.isBlank()) {
                    out.add(new AccessPath(eng, tpl));
                }
            }
        }
        return out;
    }

    /**
     * 从配置里读排除关键字:兼容两种存储形式:
     *   JSON 数组  ["tmp_", "_bak"]   （建议,前端会产出这个）
     *   单字符串   "tmp_;_bak;test"   （人工填 / 历史数据）
     * 统一 trim,去空,全部 lower-case。
     */
    private List<String> readExcludeKeywords(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        JsonNode node = parseJson(raw);
        if (node.isArray()) {
            node.forEach(v -> addKeyword(out, v.asText("")));
        } else {
            for (String part : raw.split(";")) {
                addKeyword(out, part);
            }
        }
        return out;
    }

    private void addKeyword(List<String> out, String kw) {
        if (kw == null) {
            return;
        }
        String trimmed = kw.trim();
        if (!trimmed.isEmpty()) {
            out.add(trimmed.toLowerCase());
        }
    }

    private boolean containsAnyKeyword(String tableName, List<String> keywords) {
        if (keywords.isEmpty()) {
            return false;
        }
        String lower = tableName.toLowerCase();
        for (String k : keywords) {
            if (lower.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /** 拼 "catalog.db" / "db";用于和白名单里的限定名比较。 */
    private String qualify(String catalog, String db) {
        return catalog == null ? db : catalog + "." + db;
    }

    /** 拼 "catalog.db.table" / "db.table"。 */
    private String qualify(String catalog, String db, String table) {
        return catalog == null ? db + "." + table : catalog + "." + db + "." + table;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public record SyncResult(int inserted, int updated, int skipped, int deleted, String message) {

        public static SyncResult empty(String message) {
            return new SyncResult(0, 0, 0, 0, message);
        }

        public String summary() {
            return String.format("inserted=%d updated=%d skipped=%d deleted=%d%s",
                    inserted, updated, skipped, deleted,
                    message == null || message.isBlank() ? "" : " | " + message);
        }
    }

    private record AccessPath(String engine, String template) {
    }
}
