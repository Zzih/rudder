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

import io.github.zzih.rudder.ai.rag.DocumentRetrievalService.RetrievedChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link DocumentRetriever} 适配层 —— 把 {@link DocumentRetrievalService} 包装成
 * Spring AI 2.0 Modular RAG 链路的 retriever 节点。
 * <p>
 * 不直接用 {@code VectorStoreDocumentRetriever} 的原因:
 * <ul>
 *   <li>Rudder 在 Admin 后台运行时切 Vector provider(Qdrant ↔ Pgvector ↔ Local),
 *       Spring AI 的 {@code VectorStore} 是启动期 bean,不支持热切</li>
 *   <li>未配 Vector / 未配 Embedding 时优雅降级到 MySQL FULLTEXT,Spring AI 不内建</li>
 *   <li>Workspace 可见性是"workspace_ids IS NULL OR CONTAINS currentWs"
 *       的 OR 语义,平铺 payload filter 表达不了</li>
 * </ul>
 *
 * <h3>Context keys</h3>
 * 调用方通过 {@code chatClient.prompt().advisors(a -> a.param(KEY, value))} 注入,
 * Spring AI advisor 把 {@code ChatClientRequest.context()} 透传给 {@link Query#context()}。
 * <ul>
 *   <li>{@link #CTX_WORKSPACE_ID} —— 必传,缺省时本 retriever 直接返回空 list,跳过 RAG</li>
 *   <li>{@link #CTX_ENGINE_TYPES} —— 可选,跨引擎可见性过滤(脚本上下文用)</li>
 *   <li>{@link #CTX_DOC_TYPE} —— 可选,锁定单一 docType</li>
 *   <li>{@link #CTX_TOP_K} —— 可选,默认 {@value #DEFAULT_TOP_K}</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class RudderDocumentRetriever implements DocumentRetriever {

    public static final String CTX_WORKSPACE_ID = "rudder.rag.workspaceId";
    public static final String CTX_ENGINE_TYPES = "rudder.rag.engineTypes";
    public static final String CTX_TOP_K = "rudder.rag.topK";
    public static final String CTX_DOC_TYPE = "rudder.rag.docType";

    private static final int DEFAULT_TOP_K = 5;

    private static final String META_TITLE = "title";
    private static final String META_DOC_TYPE = "docType";
    private static final String META_DOCUMENT_ID = "documentId";

    private final DocumentRetrievalService retrieval;

    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        Map<String, Object> ctx = query.context() == null ? Map.of() : query.context();
        Long workspaceId = asLong(ctx.get(CTX_WORKSPACE_ID));
        if (workspaceId == null) {
            // workspace 缺省视为"非 RAG 场景",和旧 RudderRagAdvisor 行为一致
            return List.of();
        }
        Collection<String> engineTypes = ctx.get(CTX_ENGINE_TYPES) instanceof Collection<?> c
                ? castStringCollection(c)
                : null;
        String docType = ctx.get(CTX_DOC_TYPE) instanceof String s ? s : null;
        int topK = ctx.get(CTX_TOP_K) instanceof Integer i ? i : DEFAULT_TOP_K;

        List<RetrievedChunk> chunks;
        try {
            chunks = retrieval.retrieve(workspaceId, docType, query.text(), engineTypes, topK);
        } catch (Exception e) {
            // 检索失败不应阻断主对话(LLM 退化为无 RAG 模式),只 debug 日志
            log.debug("RAG retrieval skipped: {}", e.getMessage());
            return List.of();
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Document> out = new ArrayList<>(chunks.size());
        for (RetrievedChunk c : chunks) {
            Map<String, Object> meta = new HashMap<>(4);
            if (c.getDocumentId() != null) {
                meta.put(META_DOCUMENT_ID, c.getDocumentId());
            }
            if (c.getTitle() != null) {
                meta.put(META_TITLE, c.getTitle());
            }
            if (c.getDocType() != null) {
                meta.put(META_DOC_TYPE, c.getDocType());
            }
            out.add(Document.builder()
                    .id(c.getDocumentId() == null ? null : String.valueOf(c.getDocumentId()))
                    .text(c.getChunkText() == null ? "" : c.getChunkText())
                    .metadata(meta)
                    .score((double) c.getScore())
                    .build());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> castStringCollection(Collection<?> raw) {
        for (Object o : raw) {
            if (o != null && !(o instanceof String)) {
                List<String> coerced = new ArrayList<>(raw.size());
                for (Object x : raw) {
                    coerced.add(x == null ? null : x.toString());
                }
                return coerced;
            }
        }
        return (Collection<String>) raw;
    }

    private static Long asLong(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Integer i) {
            return i.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
