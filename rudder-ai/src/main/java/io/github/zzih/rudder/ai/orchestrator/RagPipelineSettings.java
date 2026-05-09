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

/**
 * RAG 链路配置(对应 {@code t_r_rag_pipeline_config} 行的 {@code settings_json})。
 *
 * <p>所有字段都是 admin UI 可控的链路开关。Rerank provider 的**凭证**(endpoint/apiKey/model)
 * 不在此处,在 {@code t_r_spi_config} type=RERANK 行。本类只负责 stage 级别的"用不用 rerank"以及"返回前几个"。
 *
 * <p><b>JSON 兼容性</b>: 新加字段时 DB 老 row 会缺该字段,Jackson 读 record 时缺字段 → Java 默认值
 * (boolean=false / int=0)。在 compact constructor 里把 0 类规范化掉,避免 0 被误用。
 *
 * @param rewriteEnabled               是否启用 LLM query 重写(Pre-Retrieval, +1 LLM 调用)
 * @param multiQueryEnabled            是否启用多查询扩展(Pre-Retrieval, +1 LLM 调用 + N 次检索)
 * @param multiQueryCount              多查询变体数(2~5, 越大 token 成本越高)
 * @param multiQueryIncludeOriginal    多查询是否保留原始 query
 * @param compressionEnabled           是否启用多轮对话压缩(把 history + 当前 query 压成单一检索 query, +1 LLM 调用)
 * @param translationEnabled           是否启用跨语言翻译(把 query 翻成目标语言再检索, +1 LLM 调用)。
 *                                     用于"中文 query 检索英文文档"等跨语言场景
 * @param translationTargetLanguage    翻译目标语言(如 {@code english} / {@code chinese}),与 translationEnabled 配合
 * @param rerankStageEnabled           是否在 Post-Retrieval 走 rerank。需配合 type=RERANK provider 才能真启用
 * @param rerankTopN                   rerank 后保留前几个候选
 * @param keywordEnricherEnabled       入库时给每 chunk LLM 抽取关键词写到 metadata(每 chunk +1 LLM 调用,reindex 重)。
 *                                     仅对 WIKI/RUNBOOK/METRIC_DEF 生效,SCHEMA/SCRIPT 跳过(代码 hardcoded)
 * @param summaryEnricherEnabled       入库时给每 chunk LLM 抽取当前+下一段摘要(每 chunk +2 LLM 调用)。
 *                                     仅对 WIKI/RUNBOOK/METRIC_DEF 生效
 * @param augmenterAllowEmptyContext   没检索到文档时 LLM 是否仍可基于 user query 直接回答
 */
public record RagPipelineSettings(
        boolean rewriteEnabled,
        boolean multiQueryEnabled,
        int multiQueryCount,
        boolean multiQueryIncludeOriginal,
        boolean compressionEnabled,
        boolean translationEnabled,
        String translationTargetLanguage,
        boolean rerankStageEnabled,
        int rerankTopN,
        boolean keywordEnricherEnabled,
        boolean summaryEnricherEnabled,
        boolean augmenterAllowEmptyContext) {

    private static final String DEFAULT_TRANSLATION_TARGET = "english";

    public RagPipelineSettings {
        if (multiQueryCount < 1) {
            multiQueryCount = 3;
        }
        if (multiQueryCount > 5) {
            multiQueryCount = 5;
        }
        if (rerankTopN < 1) {
            rerankTopN = 5;
        }
        if (translationTargetLanguage == null || translationTargetLanguage.isBlank()) {
            translationTargetLanguage = DEFAULT_TRANSLATION_TARGET;
        }
    }

    /**
     * DB 没配置时的默认值。
     * <ul>
     *   <li>所有"会增加 LLM 调用 / token 成本"的能力默认关</li>
     *   <li>{@code augmenterAllowEmptyContext=true} 保持"无 RAG 时仍能回答"的兼容行为</li>
     * </ul>
     */
    public static RagPipelineSettings defaults() {
        return new RagPipelineSettings(
                false,
                false, 3, true,
                false,
                false, DEFAULT_TRANSLATION_TARGET,
                false, 5,
                false, false,
                true);
    }
}
