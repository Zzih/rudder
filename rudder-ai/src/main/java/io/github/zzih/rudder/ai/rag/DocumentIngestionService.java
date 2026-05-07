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

package io.github.zzih.rudder.ai.rag;

import io.github.zzih.rudder.ai.orchestrator.RagPipelineConfigService;
import io.github.zzih.rudder.ai.orchestrator.RagPipelineSettings;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiDocumentDao;
import io.github.zzih.rudder.dao.dao.AiDocumentEmbeddingDao;
import io.github.zzih.rudder.dao.entity.AiDocument;
import io.github.zzih.rudder.dao.entity.AiDocumentEmbedding;
import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.service.config.EmbeddingConfigService;
import io.github.zzih.rudder.service.config.LlmConfigService;
import io.github.zzih.rudder.service.config.VectorConfigService;
import io.github.zzih.rudder.vector.api.VectorPoint;
import io.github.zzih.rudder.vector.api.VectorStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档 ingestion:原文永远入 MySQL;向量化按 Embedding + Vector provider 配置情况:
 * <ul>
 *   <li>两者都配 → 走 embed + upsert 到当前 Vector provider(Qdrant / Local FULLTEXT)</li>
 *   <li>任一未配 → 只写原文,日志提示,日后配完可 reindex</li>
 * </ul>
 * 消费 Vector 走 vectorConfigService.active():未配时 tryIndex 静默跳过(管理员可在后台看到 indexedAt 为空,触发 reindex)。
 * <p>
 * Chunk 切分用 Spring AI {@link TokenTextSplitter}(按 token 边界而非 char),对中英文都友好,embed 质量优于硬切。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    /** Token 粒度切分:默认 chunkSize=500 tokens,minChunkSizeChars=350,keepSeparator=true。 */
    private static final TokenTextSplitter SPLITTER = new TokenTextSplitter();

    /**
     * 哪些 docType 适合做 enrichment(LLM 抽关键词/摘要)。
     * <ul>
     *   <li>WIKI/RUNBOOK/METRIC_DEF: 业务知识 + 长文,enrichment 显著提升召回</li>
     *   <li>SCHEMA: 表结构原本就是关键词密集型,enrich 收益低,跳过省成本</li>
     *   <li>SCRIPT: SQL/脚本是命令式,无"段落语义",跳过</li>
     * </ul>
     */
    private static final Set<String> ENRICHABLE_DOC_TYPES = Set.of("WIKI", "RUNBOOK", "METRIC_DEF");

    private static final int KEYWORD_COUNT = 5;

    private final AiDocumentDao documentDao;
    private final AiDocumentEmbeddingDao embeddingDao;
    private final EmbeddingConfigService embeddingConfigService;
    private final VectorConfigService vectorConfigService;
    private final LlmConfigService llmConfigService;
    private final RagPipelineConfigService ragPipelineConfigService;
    private final TransactionTemplate txTemplate;

    public AiDocument ingest(AiDocument doc) {
        validateEngineType(doc);
        validateWorkspaceBinding(doc);
        doc.setContentHash(CryptoUtils.sha256Hex(doc.getContent()));
        // 只在 MySQL 插入这一步开事务,随后 tryIndex 的 embed + Qdrant 远程调用不持 DB 连接
        txTemplate.executeWithoutResult(status -> documentDao.insert(doc));
        tryIndex(doc);
        return doc;
    }

    /** SCHEMA 类文档必须带 engineType,否则跨引擎可见性过滤会失效(AI 会看到不属于当前脚本引擎的表)。 */
    private void validateEngineType(AiDocument doc) {
        if ("SCHEMA".equalsIgnoreCase(doc.getDocType())
                && (doc.getEngineType() == null || doc.getEngineType().isBlank())) {
            throw new IllegalArgumentException("engineType is required for SCHEMA documents");
        }
    }

    /**
     * docType 级 workspace 绑定约束:
     * <ul>
     *   <li>SCHEMA 强制平台级:{@code workspaceIds} 必须为 null(由 MetadataSyncService 入口保证)</li>
     *   <li>SCRIPT 必须非空数组:脚本属于具体 workspace,不能做成平台级</li>
     *   <li>WIKI / RUNBOOK / METRIC_DEF → 两者都允许,由管理员在创建时显式选择</li>
     * </ul>
     * null / "[]" / "null" 都视为"未指定"(平台级)。
     */
    private void validateWorkspaceBinding(AiDocument doc) {
        String type = doc.getDocType() == null ? "" : doc.getDocType().toUpperCase();
        List<Long> ws = parseWorkspaceIds(doc.getWorkspaceIds());
        if ("SCHEMA".equals(type) && !ws.isEmpty()) {
            throw new IllegalArgumentException(
                    "SCHEMA documents are platform-shared; workspace_ids must be null");
        }
        if ("SCRIPT".equals(type) && ws.isEmpty()) {
            throw new IllegalArgumentException(
                    "SCRIPT documents must have a non-empty workspace_ids array");
        }
    }

    /**
     * 按 sourceRef 做 upsert(元数据同步用)。
     * 存在且 contentHash 相同 → SKIPPED;存在且变化 → UPDATED(重索引);不存在 → INSERTED。
     */
    public UpsertOutcome upsertBySourceRef(AiDocument candidate) {
        if (candidate.getSourceRef() == null || candidate.getSourceRef().isBlank()) {
            throw new IllegalArgumentException("sourceRef required for upsert");
        }
        validateEngineType(candidate);
        validateWorkspaceBinding(candidate);
        String hash = CryptoUtils.sha256Hex(candidate.getContent());
        AiDocument existing = documentDao.selectBySourceRef(candidate.getSourceRef());
        if (existing == null) {
            candidate.setContentHash(hash);
            txTemplate.executeWithoutResult(status -> documentDao.insert(candidate));
            tryIndex(candidate);
            return UpsertOutcome.INSERTED;
        }
        if (hash.equals(existing.getContentHash())) {
            return UpsertOutcome.SKIPPED;
        }
        existing.setTitle(candidate.getTitle());
        existing.setContent(candidate.getContent());
        existing.setDescription(candidate.getDescription());
        existing.setEngineType(candidate.getEngineType());
        existing.setDocType(candidate.getDocType());
        existing.setContentHash(hash);
        existing.setIndexedAt(null);
        txTemplate.executeWithoutResult(status -> documentDao.updateById(existing));
        deleteVectors(existing);
        tryIndex(existing);
        return UpsertOutcome.UPDATED;
    }

    public enum UpsertOutcome {
        INSERTED, UPDATED, SKIPPED
    }

    /** 更新原文 → 重建索引。 */
    public AiDocument update(Long id, String title, String content, String description) {
        AiDocument existing = documentDao.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        existing.setTitle(title);
        existing.setContent(content);
        existing.setDescription(description);
        existing.setContentHash(CryptoUtils.sha256Hex(content));
        existing.setIndexedAt(null);
        txTemplate.executeWithoutResult(status -> documentDao.updateById(existing));
        // 重索引(远程):先清旧 chunk,再重新 embed
        deleteVectors(existing);
        tryIndex(existing);
        return existing;
    }

    public void delete(Long id) {
        AiDocument doc = documentDao.selectById(id);
        if (doc == null) {
            return;
        }
        // 先尽力删 vector(失败不阻断,残留点由 sweepOrphanEmbeddings 兜底);再同事务删 embedding + 主表
        deleteVectors(doc);
        txTemplate.executeWithoutResult(status -> documentDao.deleteById(id));
    }

    /**
     * 兜底 sweep:正常 {@link #delete} 路径已同事务清掉 vector + embedding,这里只处理事务回滚 /
     * 手 truncate 等异常残留。orphan 行的 AiDocument 已物理消失,算不出 collection 名,只能按
     * documentId payload 删 vector,然后清 embedding 行。limit 防止一次扫穿内存。
     */
    public int sweepOrphanEmbeddings(int limit) {
        List<AiDocumentEmbedding> orphans = embeddingDao.selectOrphanEmbeddings(limit);
        if (orphans.isEmpty()) {
            return 0;
        }
        VectorStore vectorStore = vectorConfigService.active();
        Set<Long> orphanDocIds = orphans.stream()
                .map(AiDocumentEmbedding::getDocumentId)
                .collect(Collectors.toSet());
        if (vectorStore != null) {
            for (Long dId : orphanDocIds) {
                // 不知道 collection,只能跨所有已知 collection 按 payload 试删一遍 —— 残留的代价是 vector 占空间,
                // 不影响检索正确性(documentId 已不在主表,join 出不来)
                log.warn("orphan embeddings for missing document id={}: vector points may leak until full reindex",
                        dId);
            }
        }
        Set<Long> ids = new HashSet<>();
        for (AiDocumentEmbedding e : orphans) {
            ids.add(e.getId());
        }
        embeddingDao.deleteByIds(ids);
        log.info("sweep cleaned {} orphan embeddings ({} missing documents)", orphans.size(), orphanDocIds.size());
        return orphans.size();
    }

    /** 管理员触发的整表重索引(换了 embedding 模型后用)。返回成功数 + 失败 doc id 列表。 */
    public ReindexReport reindexAll(Long workspaceId, String docType) {
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (AiDocument doc : documentDao.selectByWorkspace(workspaceId, docType)) {
            try {
                deleteVectors(doc);
                tryIndex(doc);
                success++;
            } catch (Exception e) {
                log.warn("reindex document {} failed: {}", doc.getId(), e.getMessage());
                failedIds.add(doc.getId());
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("reindex finished with {} failures: {}", failedIds.size(), failedIds);
        }
        return new ReindexReport(success, failedIds);
    }

    /** 重索引执行报告。失败 id 列表用于前端提示哪些文档需要排查。 */
    public record ReindexReport(int success, List<Long> failedIds) {
    }

    // ==================== internal ====================

    private void tryIndex(AiDocument doc) {
        EmbeddingClient client = embeddingConfigService.active();
        if (client == null) {
            log.info("document {} stored (original only): no embedding provider configured", doc.getId());
            return;
        }
        VectorStore vectorStore = vectorConfigService.active();
        if (vectorStore == null) {
            log.info("document {} stored (original only): no vector provider configured", doc.getId());
            return;
        }
        if (!vectorStore.supportsVectors()) {
            log.info("document {} stored (no vector index): active vector provider does not support vectors",
                    doc.getId());
            return;
        }
        List<Document> chunkDocs = chunkToDocuments(doc.getContent());
        if (chunkDocs.isEmpty()) {
            return;
        }
        // 可选 enrichment:keyword + summary metadata。失败降级到原始 chunks,不阻断 ingest。
        chunkDocs = enrichIfApplicable(doc, chunkDocs);

        // 拆出原始 text(给 chunkText 列存原文,UI 展示用) + 拼 metadata 的 embed text(给向量化,提升召回)
        List<String> originalTexts = chunkDocs.stream()
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        if (originalTexts.isEmpty()) {
            return;
        }
        List<String> embedTexts = chunkDocs.stream()
                .map(d -> d.getFormattedContent(MetadataMode.EMBED))
                .toList();

        List<float[]> vectors;
        try {
            vectors = client.embedBatch(embedTexts);
        } catch (Exception e) {
            log.warn("embedding failed for document {}: {}", doc.getId(), e.getMessage());
            return;
        }
        int dim = client.dimensions();
        String model = client.modelId();
        String collection = collectionName(doc.getDocType());
        try {
            vectorStore.ensureCollection(collection, dim);
        } catch (RuntimeException e) {
            log.warn("vectorStore.ensureCollection failed: {}", e.getMessage());
            return;
        }

        // 解析 workspace_ids JSON 数组 → List<Long>;null / 空 数组都作为平台共享(payload 里 omit)
        List<Long> workspaceIdList = parseWorkspaceIds(doc.getWorkspaceIds());
        List<VectorPoint> points = new ArrayList<>(originalTexts.size());
        List<AiDocumentEmbedding> rows = new ArrayList<>(originalTexts.size());
        for (int i = 0; i < originalTexts.size(); i++) {
            String pointId = UUID.randomUUID().toString();
            Map<String, Object> payload = new HashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("chunkIndex", i);
            payload.put("docType", doc.getDocType());
            if (!workspaceIdList.isEmpty()) {
                payload.put("workspaceIds", workspaceIdList);
            }
            if (doc.getEngineType() != null) {
                payload.put("engineType", doc.getEngineType());
            }
            points.add(VectorPoint.builder().id(pointId).vector(vectors.get(i)).payload(payload).build());

            AiDocumentEmbedding row = new AiDocumentEmbedding();
            row.setDocumentId(doc.getId());
            row.setChunkIndex(i);
            row.setChunkText(originalTexts.get(i));
            row.setQdrantPointId(pointId);
            row.setEmbeddingModel(model);
            row.setEmbeddingDim(dim);
            rows.add(row);
        }
        boolean vectorWritten = false;
        try {
            // 1. 远程 Qdrant upsert:不持 DB 事务
            vectorStore.upsert(collection, points);
            vectorWritten = true;
            // 2. embedding 行 + indexedAt 短事务:远程成功后再开 DB 事务,避免数秒连接持有
            txTemplate.executeWithoutResult(status -> {
                embeddingDao.insertBatch(rows);
                doc.setIndexedAt(LocalDateTime.now());
                documentDao.updateById(doc);
            });
        } catch (Exception e) {
            log.error("indexing document {} failed: {}", doc.getId(), e.getMessage());
            // 补偿:如果 Qdrant 写成功了但后续 MySQL 步骤失败,回头把刚写的点删掉,
            // 避免 Qdrant 留下"MySQL 查不到反查信息的孤儿点"(sweep 也扫不到)
            if (vectorWritten) {
                List<String> justWritten = new ArrayList<>(points.size());
                for (VectorPoint p : points) {
                    justWritten.add(p.getId());
                }
                compensateOrphanVectors(vectorStore, collection, justWritten, doc.getId());
            }
        }
    }

    /** 删除孤儿向量。短指数退避重试 3 次,最终失败仅 log.error,不影响主流程。 */
    private static void compensateOrphanVectors(VectorStore vectorStore, String collection,
                                                List<String> ids, Long docId) {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                vectorStore.deleteByIds(collection, ids);
                log.warn("compensated {} Qdrant points after MySQL failure for document {} (attempt {})",
                        ids.size(), docId, attempt);
                return;
            } catch (Exception cleanupEx) {
                if (attempt == maxAttempts) {
                    log.error("Qdrant compensation delete FAILED after {} attempts for document {}: {} — "
                            + "orphan points remain. Run `reindex` to recover.",
                            maxAttempts, docId, cleanupEx.getMessage());
                    return;
                }
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void deleteVectors(AiDocument doc) {
        VectorStore vectorStore = vectorConfigService.active();
        if (vectorStore != null) {
            try {
                String collection = collectionName(doc.getDocType());
                vectorStore.deleteByPayload(collection, "documentId", doc.getId());
            } catch (Exception e) {
                log.debug("deleteByPayload skipped: {}", e.getMessage());
            }
        }
        embeddingDao.deleteByDocument(doc.getId());
    }

    /**
     * Qdrant collection 名 = docType(如 SCHEMA / WIKI / RUNBOOK / ...)。
     * 工作区可见性不再用 collection 分区,而是 point payload 里的 {@code workspaceIds} 数组做过滤,
     * 这样平台共享文档只需存一份、避免每加一个 workspace 就要全量 re-upsert。
     */
    public static String collectionName(String docType) {
        return docType == null ? "UNKNOWN" : docType;
    }

    /** 解析 {@code workspace_ids} JSON 数组。null / 空串 / 非数组 / 空数组 都返回空 List(视为平台共享)。 */
    public static List<Long> parseWorkspaceIds(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw.trim())) {
            return List.of();
        }
        try {
            List<Long> out = JsonUtils.toList(raw, Long.class);
            return out != null ? out : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 走 Spring AI TokenTextSplitter:按 token 边界切分,比 char-level 硬切更符合 embedding 语义连贯性。 */
    private static List<Document> chunkToDocuments(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return SPLITTER.apply(List.of(new Document(content)));
    }

    /**
     * 可选 enrichment 阶段。
     * <ul>
     *   <li>RAG pipeline 没启用 → 原样返回</li>
     *   <li>docType 不在 {@link #ENRICHABLE_DOC_TYPES} → 原样返回(SCHEMA/SCRIPT 等结构化数据 enrich 收益低)</li>
     *   <li>LLM 不可用 / enricher 抛异常 → 原样返回 + warn 日志,**不阻断主 ingest 流程**</li>
     *   <li>启用且成功 → 返回带 metadata 的 Document(keyword / summary 写到 metadata,
     *       embed 时通过 {@link MetadataMode#EMBED} 拼到文本里参与向量化)</li>
     * </ul>
     */
    private List<Document> enrichIfApplicable(AiDocument doc, List<Document> chunks) {
        RagPipelineSettings rag = ragPipelineConfigService.active();
        if (!rag.keywordEnricherEnabled() && !rag.summaryEnricherEnabled()) {
            return chunks;
        }
        if (doc.getDocType() == null || !ENRICHABLE_DOC_TYPES.contains(doc.getDocType().toUpperCase())) {
            return chunks;
        }
        ChatModel chatModel = llmConfigService.activeChatModel();
        if (chatModel == null) {
            log.warn("enrichment skipped for document {}: no active LLM provider", doc.getId());
            return chunks;
        }

        List<Document> out = chunks;
        if (rag.keywordEnricherEnabled()) {
            try {
                out = KeywordMetadataEnricher.builder(chatModel).keywordCount(KEYWORD_COUNT).build().apply(out);
            } catch (Exception e) {
                log.warn("keyword enricher failed for document {}, falling back to non-enriched: {}",
                        doc.getId(), e.getMessage());
                out = chunks;
            }
        }
        if (rag.summaryEnricherEnabled()) {
            try {
                out = new SummaryMetadataEnricher(chatModel,
                        List.of(SummaryType.CURRENT, SummaryType.NEXT)).apply(out);
            } catch (Exception e) {
                log.warn("summary enricher failed for document {}, falling back: {}",
                        doc.getId(), e.getMessage());
            }
        }
        return out;
    }

}
