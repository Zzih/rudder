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

package io.github.zzih.rudder.ai.orchestrator;

import io.github.zzih.rudder.ai.orchestrator.advisor.RedactionAdvisor;
import io.github.zzih.rudder.ai.orchestrator.advisor.UsageMetricsAdvisor;
import io.github.zzih.rudder.ai.rag.DocumentRetrievalService;
import io.github.zzih.rudder.ai.rag.RudderDocumentRetriever;
import io.github.zzih.rudder.ai.rerank.RerankConfigService;
import io.github.zzih.rudder.ai.rerank.RerankDocumentPostProcessor;
import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.service.redaction.RedactionService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AGENT 和 CHAT 两条路径共享的 ChatClient 构造 —— 统一挂 4 个 advisor:
 * SimpleLoggerAdvisor / RetrievalAugmentationAdvisor (Spring AI 2.0 Modular RAG) /
 * RedactionAdvisor / UsageMetricsAdvisor。每个 turn 一次性构造,
 * advisor 里的计数器用 turn 作用域的 {@link AtomicInteger} 持有,不是 bean 单例。
 *
 * <h3>RAG 链路 (Spring AI 2.0)</h3>
 * <ul>
 *   <li>Pre-Retrieval: {@link RewriteQueryTransformer} / {@link MultiQueryExpander} (开关来自 {@link RagPipelineConfigService},admin UI 控制)</li>
 *   <li>Retrieval: {@link RudderDocumentRetriever} (适配自研 {@link DocumentRetrievalService},
 *       保留 FULLTEXT 兜底 / 运行时切 VectorStore / workspace OR 过滤)</li>
 *   <li>Document Joiner: Spring AI 默认 {@code ConcatenationDocumentJoiner} (合并多变体结果 + 去重)</li>
 *   <li>Post-Retrieval: {@link RerankDocumentPostProcessor} (cross-encoder 精排,
 *       启用 = pipeline 开关 ON 且 admin 配了 rerank provider)</li>
 *   <li>Generation: {@link ContextualQueryAugmenter} (替代手写 PromptTemplate,
 *       allowEmptyContext 由 admin UI 控制)</li>
 * </ul>
 *
 * <p><b>防递归</b>: rewrite/expand 阶段会调用 LLM,这些 LLM 调用必须用**不带 RAG advisor**的 vanilla
 * {@link ChatClient.Builder} ({@link #vanillaChatClientBuilder}),否则会触发"重写 → 检索 → 重写"无限递归。
 *
 * <p>所有 RAG 链路开关在 {@code t_r_rag_pipeline_config},Rerank provider 在 {@code t_r_spi_config}
 * type=RERANK 行,均通过 admin UI 配置,不读 yml。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientFactory {

    private static final int RAG_ADVISOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;

    private final DocumentRetrievalService documentRetrievalService;
    private final RedactionService redactionService;
    private final RagPipelineConfigService ragPipelineConfigService;
    private final RerankConfigService rerankConfigService;

    /**
     * 构造 ChatClient。{@code ragEnabled=false} 时**完全不挂 RAG advisor**,避免
     * compression/translation/rewrite 等 query transformer 在 RAG 关闭时仍白调 LLM。
     *
     * @param ragEnabled per-turn 总开关,通常来自 {@code AiContextProfile.injectWikiRag}。
     *                   eval / 测试场景永远传 true
     */
    public ChatClient build(ChatModel chatModel, Long workspaceId,
                            AtomicInteger promptTokens, AtomicInteger completionTokens,
                            boolean ragEnabled) {
        List<Advisor> advisors = new ArrayList<>(4);
        advisors.add(new SimpleLoggerAdvisor());
        if (ragEnabled) {
            advisors.add(buildRagAdvisor(chatModel));
        }
        advisors.add(new RedactionAdvisor(redactionService));
        advisors.add(new UsageMetricsAdvisor(promptTokens, completionTokens));
        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisors)
                .build();
    }

    private Advisor buildRagAdvisor(ChatModel chatModel) {
        RagPipelineSettings pipeline = ragPipelineConfigService.active();

        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(new RudderDocumentRetriever(documentRetrievalService))
                .order(RAG_ADVISOR_ORDER);

        // Pre-Retrieval Query Transformer 链(顺序: compression → translation → rewrite,
        // 多轮先压成单 query,再翻译,最后改写提升召回)
        // 每个 transformer 都包 fail-safe: LLM 失败时退回原 query,**不阻断主对话**
        List<QueryTransformer> transformers = new ArrayList<>();
        if (pipeline.compressionEnabled()) {
            transformers.add(failSafe("compression", CompressionQueryTransformer.builder()
                    .chatClientBuilder(vanillaChatClientBuilder(chatModel))
                    .build()));
        }
        if (pipeline.translationEnabled()) {
            transformers.add(failSafe("translation", TranslationQueryTransformer.builder()
                    .chatClientBuilder(vanillaChatClientBuilder(chatModel))
                    .targetLanguage(pipeline.translationTargetLanguage())
                    .build()));
        }
        if (pipeline.rewriteEnabled()) {
            transformers.add(failSafe("rewrite", RewriteQueryTransformer.builder()
                    .chatClientBuilder(vanillaChatClientBuilder(chatModel))
                    .build()));
        }
        if (!transformers.isEmpty()) {
            builder.queryTransformers(transformers.toArray(new QueryTransformer[0]));
        }

        if (pipeline.multiQueryEnabled()) {
            builder.queryExpander(failSafeExpander(MultiQueryExpander.builder()
                    .chatClientBuilder(vanillaChatClientBuilder(chatModel))
                    .numberOfQueries(pipeline.multiQueryCount())
                    .includeOriginal(pipeline.multiQueryIncludeOriginal())
                    .build()));
        }

        DocumentPostProcessor rerankProcessor = buildRerankProcessor(pipeline);
        if (rerankProcessor != null) {
            builder.documentPostProcessors(rerankProcessor);
        }

        builder.queryAugmenter(ContextualQueryAugmenter.builder()
                .allowEmptyContext(pipeline.augmenterAllowEmptyContext())
                .build());

        return builder.build();
    }

    /**
     * 给 RAG 内部 LLM 调用 (rewrite / expand) 用 —— 不挂任何 advisor,避免递归 RAG。
     */
    private static ChatClient.Builder vanillaChatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * 构建 rerank PostProcessor。启用条件:
     * <ol>
     *   <li>RAG pipeline 配置 {@code rerankStageEnabled=true}</li>
     *   <li>Admin UI 配了 rerank provider 且 {@code enabled=true}</li>
     * </ol>
     * 任一不满足返回 null,advisor 跳过 Post-Retrieval 阶段。
     */
    private DocumentPostProcessor buildRerankProcessor(RagPipelineSettings pipeline) {
        if (!pipeline.rerankStageEnabled()) {
            return null;
        }
        RerankClient client = rerankConfigService.active();
        if (client == null) {
            return null;
        }
        return new RerankDocumentPostProcessor(client, pipeline.rerankTopN());
    }

    /**
     * 把 transformer 包成 fail-safe: 任何异常(LLM 404 / 超时 / provider 不支持 sync 等)
     * 都退回原 query 让主对话继续。否则一个 RAG 子调用失败会把整个 turn 炸掉(用户体验差)。
     */
    private static QueryTransformer failSafe(String name, QueryTransformer delegate) {
        return query -> {
            try {
                return delegate.transform(query);
            } catch (Exception e) {
                log.warn("query transformer [{}] failed, falling back to original query: {} ({})",
                        name, e.getClass().getSimpleName(), e.getMessage());
                return query;
            }
        };
    }

    /**
     * 同上,但针对 1 → N 的 expander。失败退回 List.of(原 query) 等价于 expand 关闭。
     */
    private static QueryExpander failSafeExpander(QueryExpander delegate) {
        return new QueryExpander() {

            @Override
            public List<Query> expand(Query query) {
                try {
                    return delegate.expand(query);
                } catch (Exception e) {
                    log.warn("query expander failed, falling back to single original query: {} ({})",
                            e.getClass().getSimpleName(), e.getMessage());
                    return List.of(query);
                }
            }
        };
    }
}
