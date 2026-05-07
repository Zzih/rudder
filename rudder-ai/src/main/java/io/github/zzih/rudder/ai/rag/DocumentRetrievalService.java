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

import io.github.zzih.rudder.common.enums.error.AiErrorCode;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.page.PageRequest;
import io.github.zzih.rudder.dao.dao.AiDocumentDao;
import io.github.zzih.rudder.dao.dao.AiDocumentEmbeddingDao;
import io.github.zzih.rudder.dao.entity.AiDocument;
import io.github.zzih.rudder.dao.entity.AiDocumentEmbedding;
import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.service.config.EmbeddingConfigService;
import io.github.zzih.rudder.service.config.VectorConfigService;
import io.github.zzih.rudder.vector.api.VectorQuery;
import io.github.zzih.rudder.vector.api.VectorSearchHit;
import io.github.zzih.rudder.vector.api.VectorStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一检索入口。按 Admin 后台配置的 Vector provider 工作:
 * <ul>
 *   <li>配 QDRANT + Embedding → 语义检索(Qdrant topK)</li>
 *   <li>配 LOCAL → 语义返回空,自动走 MySQL FULLTEXT</li>
 *   <li>未配 Vector → 直接走 MySQL FULLTEXT(不强制要求 provider,ContextBuilder 自动 RAG 场景静默)</li>
 * </ul>
 * agent 主动调 {@code search_documents} 工具时同样走此入口,管理员未配 Vector 的情况下仍能返回关键词召回结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRetrievalService {

    private final AiDocumentDao documentDao;
    private final AiDocumentEmbeddingDao embeddingDao;
    private final EmbeddingConfigService embeddingConfigService;
    private final VectorConfigService vectorConfigService;

    public List<RetrievedChunk> retrieve(Long workspaceId, String docType, String query, int topK) {
        return retrieve(workspaceId, docType, query, null, topK);
    }

    /**
     * 检索入口。engineTypes 为跨引擎可见性过滤:
     * <ul>
     *   <li>null → 不过滤(非脚本上下文)</li>
     *   <li>非空 → 只返回 engineType IS NULL(引擎无关文档) 或 engineType ∈ 给定集合的 chunk</li>
     * </ul>
     */
    public List<RetrievedChunk> retrieve(Long workspaceId, String docType, String query,
                                         Collection<String> engineTypes, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = topK <= 0 ? 5 : Math.min(topK, 20);
        Set<String> engineFilter = normalizeEngines(engineTypes);

        EmbeddingClient client = embeddingConfigService.active();
        VectorStore vectorStore = vectorConfigService.active();
        if (client != null && vectorStore != null && vectorStore.supportsVectors()) {
            List<RetrievedChunk> semantic = semanticSearchAcrossDocTypes(
                    client, vectorStore, workspaceId, docType, query, engineFilter, limit);
            if (!semantic.isEmpty()) {
                return semantic;
            }
            log.debug("semantic search empty, fall through to FULLTEXT");
        }
        return fulltextSearch(workspaceId, docType, query, engineFilter, limit);
    }

    /** 每 collection = docType 本身。docType 不指定时遍历当前 workspace 可见的所有类型,合并取 topK。 */
    private List<RetrievedChunk> semanticSearchAcrossDocTypes(
                                                              EmbeddingClient client, VectorStore vectorStore,
                                                              Long workspaceId, String docType, String query,
                                                              Set<String> engineFilter, int limit) {
        if (docType != null) {
            return semanticSearch(client, vectorStore, workspaceId, docType, query, engineFilter, limit);
        }
        List<String> docTypes = documentDao.listDistinctDocTypes(workspaceId);
        if (docTypes == null || docTypes.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> merged = new ArrayList<>();
        for (String type : docTypes) {
            merged.addAll(semanticSearch(client, vectorStore, workspaceId, type, query, engineFilter, limit));
        }
        // 按 score 降序取 topK
        merged.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return merged.size() <= limit ? merged : merged.subList(0, limit);
    }

    private Set<String> normalizeEngines(Collection<String> engines) {
        if (engines == null || engines.isEmpty()) {
            return null;
        }
        Set<String> out = new HashSet<>();
        for (String e : engines) {
            if (e != null && !e.isBlank()) {
                out.add(e.toUpperCase());
            }
        }
        return out.isEmpty() ? null : out;
    }

    private List<RetrievedChunk> semanticSearch(EmbeddingClient client, VectorStore vectorStore,
                                                Long workspaceId, String docType, String query,
                                                Set<String> engineFilter, int limit) {
        try {
            float[] vec = client.embed(query);
            String collection = DocumentIngestionService.collectionName(docType);
            Map<String, Object> filter = new HashMap<>();
            if (docType != null) {
                filter.put("docType", docType);
            }
            // 过滤(workspace / engine)都在命中后用 AiDocument 元信息重新校验,VectorStore payload filter
            // 只做粗匹配(docType 精确)。workspace 过滤靠后置,是因为我们的 filter 需要"平台共享(workspaceIds
            // 缺失) ∪ 数组包含当前 workspace"语义,现有 flat-map payload filter 无法表达这种 OR。
            boolean needPostFilter = workspaceId != null || engineFilter != null;
            int fetch = needPostFilter ? Math.min(limit * 4, 80) : limit;
            VectorQuery q = VectorQuery.builder()
                    .collection(collection)
                    .queryVector(vec)
                    .queryText(query)
                    .topK(fetch)
                    .payloadFilter(filter.isEmpty() ? null : filter)
                    .build();
            List<VectorSearchHit> hits = vectorStore.search(q);
            if (hits.isEmpty()) {
                return List.of();
            }
            List<String> pointIds = new ArrayList<>(hits.size());
            for (VectorSearchHit h : hits) {
                pointIds.add(h.getId());
            }
            Map<String, AiDocumentEmbedding> byPoint = new HashMap<>();
            Set<Long> docIds = new HashSet<>();
            for (AiDocumentEmbedding e : embeddingDao.selectByPointIds(pointIds)) {
                byPoint.put(e.getQdrantPointId(), e);
                docIds.add(e.getDocumentId());
            }
            // 一次批量拉取所有命中的 document,避免 per-hit selectById 的 N+1
            Map<Long, AiDocument> docById = new HashMap<>(docIds.size());
            for (AiDocument d : documentDao.selectByIds(docIds)) {
                docById.put(d.getId(), d);
            }
            List<RetrievedChunk> out = new ArrayList<>(hits.size());
            for (VectorSearchHit h : hits) {
                if (out.size() >= limit) {
                    break;
                }
                AiDocumentEmbedding e = byPoint.get(h.getId());
                if (e == null) {
                    continue;
                }
                AiDocument doc = docById.get(e.getDocumentId());
                if (doc == null) {
                    continue;
                }
                if (!visibleToWorkspace(doc, workspaceId)) {
                    continue;
                }
                if (engineFilter != null && doc.getEngineType() != null
                        && !engineFilter.contains(doc.getEngineType().toUpperCase())) {
                    continue;
                }
                out.add(new RetrievedChunk(doc.getId(), doc.getTitle(), e.getChunkText(), h.getScore(),
                        doc.getDocType()));
            }
            return out;
        } catch (Exception e) {
            log.warn("semantic search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 当前 workspace 实际存在的所有 docType(含平台共享)。MCP 浏览/前端类目导航用。 */
    public List<String> listDocTypes(Long workspaceId) {
        List<String> raw = documentDao.listDistinctDocTypes(workspaceId);
        return raw == null ? List.of() : raw;
    }

    /** 分页列出文档元信息(不含 content,workspace 严格隔离 + 平台共享 OR)。docType 可空。 */
    public IPage<AiDocument> listDocuments(Long workspaceId, String docType, int pageNum, int pageSize) {
        int p = PageRequest.normalizePageNum(pageNum);
        int s = PageRequest.normalizePageSize(pageSize);
        return documentDao.selectMetaPage(workspaceId, docType == null || docType.isBlank() ? null : docType, p, s);
    }

    /** 按 id 取文档,含可见性校验。不存在或当前 workspace 不可见时统一抛 NotFoundException(避免泄露存在性)。 */
    public AiDocument getDocument(Long workspaceId, Long id) {
        if (id == null) {
            throw new NotFoundException(AiErrorCode.DOCUMENT_NOT_FOUND, "null");
        }
        AiDocument doc = documentDao.selectById(id);
        if (doc == null || !visibleToWorkspace(doc, workspaceId)) {
            throw new NotFoundException(AiErrorCode.DOCUMENT_NOT_FOUND, id);
        }
        return doc;
    }

    /**
     * AiDocument 是否对当前 workspace 可见。
     * <ul>
     *   <li>doc.workspace_ids IS NULL / 空 → 平台共享,任何 workspace 都可见</li>
     *   <li>doc.workspace_ids 是数组且包含 currentWs → 可见</li>
     *   <li>否则 → 不可见</li>
     * </ul>
     * currentWs 为 null(例如 admin 调试场景)时视为平台视角,全部可见。
     */
    private static boolean visibleToWorkspace(AiDocument doc, Long currentWs) {
        if (currentWs == null) {
            return true;
        }
        List<Long> docWs = DocumentIngestionService.parseWorkspaceIds(doc.getWorkspaceIds());
        return docWs.isEmpty() || docWs.contains(currentWs);
    }

    private List<RetrievedChunk> fulltextSearch(Long workspaceId, String docType, String query,
                                                Set<String> engineFilter, int limit) {
        List<AiDocument> docs = documentDao.fulltextSearch(query, workspaceId, docType, engineFilter, limit);
        List<RetrievedChunk> out = new ArrayList<>(docs.size());
        for (AiDocument d : docs) {
            String preview = d.getContent() != null && d.getContent().length() > 400
                    ? d.getContent().substring(0, 400) + "..."
                    : d.getContent();
            out.add(new RetrievedChunk(d.getId(), d.getTitle(), preview, 0f, d.getDocType()));
        }
        return out;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetrievedChunk {

        private Long documentId;
        private String title;
        private String chunkText;
        private float score;
        private String docType;
    }
}
