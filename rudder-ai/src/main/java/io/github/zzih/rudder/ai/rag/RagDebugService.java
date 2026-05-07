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
import io.github.zzih.rudder.ai.rag.RagDebugTrace.StageTrace;
import io.github.zzih.rudder.ai.rerank.RerankConfigService;
import io.github.zzih.rudder.ai.rerank.RerankDocumentPostProcessor;
import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.service.config.LlmConfigService;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG retrieval 调试服务 —— admin UI 输入 query,本服务**手动跑一遍**生产 RAG 链路各 stage,
 * 收集每阶段 input/output/duration/error 返回给前端可视化。
 *
 * <p>**与生产链路解耦**: 不复用 Spring AI {@code RetrievalAugmentationAdvisor}(它是黑盒,
 * 不暴露 stage 级 hook)。这里手动一步步执行,保证可观察。配置从
 * {@link RagPipelineConfigService} 读,确保 debug 看到的就是生产正在用的链路。
 *
 * <p>**fail-safe**: 各 stage 失败时 trace 记 error 字段,继续后续 stage(原 query 透传),
 * 不抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDebugService {

    private static final int CHUNK_TEXT_PREVIEW_LIMIT = 200;
    private static final int PREVIEW_DOC_LIMIT = 20;

    private final RagPipelineConfigService ragPipelineConfigService;
    private final RerankConfigService rerankConfigService;
    private final LlmConfigService llmConfigService;
    private final DocumentRetrievalService documentRetrievalService;

    public RagDebugTrace debug(String userQuery, Long workspaceId, TaskType taskType) {
        long start = System.currentTimeMillis();

        RagPipelineSettings pipeline = ragPipelineConfigService.active();
        ChatModel chatModel = llmConfigService.activeChatModel();
        // 一次构造,各 transformer 共用 —— 避免每个 stage 重新 ChatClient.builder(chatModel)
        ChatClient.Builder cb = chatModel == null ? null : ChatClient.builder(chatModel);

        RagDebugTrace trace = RagDebugTrace.builder()
                .originalQuery(userQuery)
                .ragEnabled(true)
                .pipelineSnapshot(snapshot(pipeline))
                .stages(new ArrayList<>())
                .build();

        DocumentRetriever retriever = new RudderDocumentRetriever(documentRetrievalService);
        Map<String, Object> context = buildRetrieverContext(workspaceId, taskType);
        Query currentQuery = Query.builder()
                .text(userQuery == null ? "" : userQuery)
                .context(context)
                .build();

        // ============== Pre-Retrieval ==============
        currentQuery = runTransformer(trace, "compression", pipeline.compressionEnabled(),
                "disabled in pipeline", currentQuery,
                () -> cb == null ? null
                        : CompressionQueryTransformer.builder().chatClientBuilder(cb).build());
        currentQuery = runTransformer(trace, "translation", pipeline.translationEnabled(),
                "disabled in pipeline", currentQuery,
                () -> cb == null ? null
                        : TranslationQueryTransformer.builder()
                                .chatClientBuilder(cb)
                                .targetLanguage(pipeline.translationTargetLanguage())
                                .build());
        currentQuery = runTransformer(trace, "rewrite", pipeline.rewriteEnabled(),
                "disabled in pipeline", currentQuery,
                () -> cb == null ? null
                        : RewriteQueryTransformer.builder().chatClientBuilder(cb).build());

        // ============== Multi-Query expansion ==============
        List<Query> queries = runMultiQuery(trace, pipeline, cb, currentQuery);

        // ============== Retrieval (per query) ==============
        Map<Query, List<List<Document>>> retrievedPerQuery =
                runRetrieval(trace, retriever, queries);

        // ============== Joiner ==============
        List<Document> joined = runJoin(trace, retrievedPerQuery);

        // ============== Rerank ==============
        joined = runRerank(trace, pipeline, currentQuery, joined);

        // ============== Augmenter (final prompt) ==============
        runAugment(trace, pipeline, currentQuery, joined);

        trace.setTotalLatencyMs((int) (System.currentTimeMillis() - start));
        return trace;
    }

    // ==================== stages ====================

    private Query runTransformer(RagDebugTrace trace, String name, boolean enabled, String skipReason,
                                 Query input,
                                 java.util.function.Supplier<QueryTransformer> factory) {
        if (!enabled) {
            trace.getStages().add(StageTrace.skipped(name, skipReason));
            return input;
        }
        QueryTransformer transformer = factory.get();
        if (transformer == null) {
            trace.getStages().add(StageTrace.skipped(name, "no LLM provider available"));
            return input;
        }
        long t0 = System.currentTimeMillis();
        try {
            Query out = transformer.transform(input);
            trace.getStages().add(StageTrace.builder()
                    .name(name)
                    .input(input.text())
                    .output(out.text())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return out;
        } catch (Exception e) {
            trace.getStages().add(StageTrace.builder()
                    .name(name)
                    .input(input.text())
                    .output(input.text())
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return input;
        }
    }

    private List<Query> runMultiQuery(RagDebugTrace trace, RagPipelineSettings pipeline,
                                      ChatClient.Builder cb, Query input) {
        if (!pipeline.multiQueryEnabled()) {
            trace.getStages().add(StageTrace.skipped("multi-query", "disabled in pipeline"));
            return List.of(input);
        }
        if (cb == null) {
            trace.getStages().add(StageTrace.skipped("multi-query", "no LLM provider available"));
            return List.of(input);
        }
        long t0 = System.currentTimeMillis();
        try {
            MultiQueryExpander expander = MultiQueryExpander.builder()
                    .chatClientBuilder(cb)
                    .numberOfQueries(pipeline.multiQueryCount())
                    .includeOriginal(pipeline.multiQueryIncludeOriginal())
                    .build();
            List<Query> out = expander.expand(input);
            trace.getStages().add(StageTrace.builder()
                    .name("multi-query")
                    .input(input.text())
                    .output(out.stream().map(Query::text).toList())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return out;
        } catch (Exception e) {
            trace.getStages().add(StageTrace.builder()
                    .name("multi-query")
                    .input(input.text())
                    .output(List.of(input.text()))
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return List.of(input);
        }
    }

    private Map<Query, List<List<Document>>> runRetrieval(RagDebugTrace trace,
                                                          DocumentRetriever retriever,
                                                          List<Query> queries) {
        long t0 = System.currentTimeMillis();
        Map<Query, List<List<Document>>> out = new LinkedHashMap<>();
        List<Map<String, Object>> perQueryView = new ArrayList<>();
        for (Query q : queries) {
            try {
                List<Document> docs = retriever.retrieve(q);
                out.put(q, List.of(docs));
                perQueryView.add(Map.of(
                        "query", q.text(),
                        "hits", docs.size(),
                        "preview", previewDocs(docs)));
            } catch (Exception e) {
                out.put(q, List.of(List.of()));
                perQueryView.add(Map.of(
                        "query", q.text(),
                        "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        trace.getStages().add(StageTrace.builder()
                .name("retrieval")
                .input(queries.stream().map(Query::text).toList())
                .output(perQueryView)
                .durationMs((int) (System.currentTimeMillis() - t0))
                .build());
        return out;
    }

    private List<Document> runJoin(RagDebugTrace trace, Map<Query, List<List<Document>>> retrieved) {
        long t0 = System.currentTimeMillis();
        DocumentJoiner joiner = new ConcatenationDocumentJoiner();
        try {
            List<Document> joined = joiner.join(retrieved);
            trace.getStages().add(StageTrace.builder()
                    .name("joiner")
                    .input(retrieved.values().stream().mapToInt(l -> l.stream().mapToInt(List::size).sum()).sum())
                    .output(Map.of("merged", joined.size(), "preview", previewDocs(joined)))
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return joined;
        } catch (Exception e) {
            trace.getStages().add(StageTrace.builder()
                    .name("joiner")
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return List.of();
        }
    }

    private List<Document> runRerank(RagDebugTrace trace, RagPipelineSettings pipeline,
                                     Query query, List<Document> docs) {
        if (!pipeline.rerankStageEnabled()) {
            trace.getStages().add(StageTrace.skipped("rerank", "disabled in pipeline"));
            return docs;
        }
        RerankClient client = rerankConfigService.active();
        if (client == null) {
            trace.getStages().add(StageTrace.skipped("rerank", "no rerank provider configured"));
            return docs;
        }
        long t0 = System.currentTimeMillis();
        try {
            List<Document> reranked = new RerankDocumentPostProcessor(client, pipeline.rerankTopN())
                    .process(query, docs);
            trace.getStages().add(StageTrace.builder()
                    .name("rerank")
                    .input(Map.of("input", docs.size(), "model", client.modelId(), "topN", pipeline.rerankTopN()))
                    .output(Map.of("output", reranked.size(), "preview", previewDocs(reranked)))
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return reranked;
        } catch (Exception e) {
            trace.getStages().add(StageTrace.builder()
                    .name("rerank")
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            return docs;
        }
    }

    private void runAugment(RagDebugTrace trace, RagPipelineSettings pipeline,
                            Query query, List<Document> docs) {
        long t0 = System.currentTimeMillis();
        try {
            ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                    .allowEmptyContext(pipeline.augmenterAllowEmptyContext())
                    .build();
            Query augmented = augmenter.augment(query, docs);
            trace.setFinalPrompt(augmented.text());
            trace.getStages().add(StageTrace.builder()
                    .name("augmenter")
                    .input(Map.of("query", query.text(), "context_docs", docs.size()))
                    .output(augmented.text())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
        } catch (Exception e) {
            trace.getStages().add(StageTrace.builder()
                    .name("augmenter")
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - t0))
                    .build());
            trace.setFinalPrompt(query.text());
        }
    }

    // ==================== helpers ====================

    private Map<String, Object> buildRetrieverContext(Long workspaceId, TaskType taskType) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (workspaceId != null) {
            ctx.put(RudderDocumentRetriever.CTX_WORKSPACE_ID, workspaceId);
        }
        if (taskType != null) {
            // 简单映射: 直接拿 datasourceType 字段当 engineType filter
            String engine = taskType.getDatasourceType();
            if (engine != null && !engine.isBlank()) {
                Set<String> engines = new LinkedHashSet<>();
                engines.add(engine.toUpperCase());
                ctx.put(RudderDocumentRetriever.CTX_ENGINE_TYPES, engines);
            }
        }
        return ctx;
    }

    /** 截断到 {@value #PREVIEW_DOC_LIMIT} 个候选避免 trace 响应体爆炸 (e.g. multi-query × 大 topK)。 */
    private static List<Map<String, Object>> previewDocs(List<Document> docs) {
        List<Map<String, Object>> out = new ArrayList<>(Math.min(docs.size(), PREVIEW_DOC_LIMIT));
        int limit = Math.min(docs.size(), PREVIEW_DOC_LIMIT);
        for (int i = 0; i < limit; i++) {
            Document d = docs.get(i);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", d.getId());
            v.put("score", d.getScore());
            v.put("text", org.apache.commons.lang3.StringUtils.abbreviate(d.getText(), CHUNK_TEXT_PREVIEW_LIMIT));
            v.put("metadata", d.getMetadata());
            out.add(v);
        }
        if (docs.size() > limit) {
            out.add(Map.of("_truncated", "...+" + (docs.size() - limit) + " more"));
        }
        return out;
    }

    private static Map<String, Object> snapshot(RagPipelineSettings p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rewriteEnabled", p.rewriteEnabled());
        m.put("compressionEnabled", p.compressionEnabled());
        m.put("translationEnabled", p.translationEnabled());
        m.put("translationTargetLanguage", p.translationTargetLanguage());
        m.put("multiQueryEnabled", p.multiQueryEnabled());
        m.put("multiQueryCount", p.multiQueryCount());
        m.put("multiQueryIncludeOriginal", p.multiQueryIncludeOriginal());
        m.put("rerankStageEnabled", p.rerankStageEnabled());
        m.put("rerankTopN", p.rerankTopN());
        m.put("augmenterAllowEmptyContext", p.augmenterAllowEmptyContext());
        return m;
    }

}
