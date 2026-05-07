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

package io.github.zzih.rudder.ai.eval;

import io.github.zzih.rudder.service.config.LlmConfigService;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI 2.0 语义评估器封装 —— LLM-as-judge 自动给 eval case 打分。
 *
 * <h3>两种评估</h3>
 * <ul>
 *   <li>{@link RelevancyEvaluator} —— 判定 response 是否回答了 query (基于 retrieved context),
 *       适用于所有 RAG 类 case</li>
 *   <li>{@link FactCheckingEvaluator} —— 判定 response 中的事实是否被 retrieved context 支持,
 *       检测幻觉。**仅在 retrieved context 非空时有意义**</li>
 * </ul>
 *
 * <h3>失败降级</h3>
 * 评估器内部跑 sync LLM 调用 (.call()), 如果配的 LLM 不支持 sync 或调用挂了,
 * 不阻断 eval 主流程,只是返回 score=null 表示"无语义评分"。
 *
 * <h3>评分计算</h3>
 * 现阶段 Spring AI Evaluator 只输出 boolean pass/fail (RelevancyEvaluator 返回 YES/NO,
 * EvaluationResponse.isPass 为 true/false), 没有连续分数。我们规则化映射:
 * <pre>
 *   relevancy   pass + factChecking pass = 100
 *   relevancy   pass + factChecking fail =  60   (回答相关但有幻觉)
 *   relevancy   pass + factChecking N/A  = 100   (无 RAG context, 不做事实核查)
 *   relevancy   fail                     =   0
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSemanticEvaluator {

    private final LlmConfigService llmConfigService;

    /**
     * 跑两个 evaluator + 综合评分。executionSucceeded()=false 时直接返回 unavailable
     * (主链路失败,没东西可评)。
     */
    public Verdict evaluate(String userQuery, OneshotResult result) {
        if (result == null || !result.executionSucceeded() || result.getFinalText() == null
                || result.getFinalText().isBlank()) {
            return Verdict.unavailable("execution did not produce a response");
        }
        ChatClient.Builder judgeBuilder = tryJudgeChatClientBuilder();
        if (judgeBuilder == null) {
            return Verdict.unavailable("no LLM provider available for evaluator judge");
        }

        EvaluationRequest req = new EvaluationRequest(
                userQuery == null ? "" : userQuery,
                result.getRetrievedContext(),
                result.getFinalText());

        Boolean relevancy = runRelevancy(judgeBuilder, req);
        Boolean factChecking = result.getRetrievedContext() == null || result.getRetrievedContext().isEmpty()
                ? null
                : runFactChecking(judgeBuilder, req);

        Integer score = computeScore(relevancy, factChecking);
        return new Verdict(relevancy, factChecking, score, buildFeedback(relevancy, factChecking));
    }

    private Boolean runRelevancy(ChatClient.Builder judgeBuilder, EvaluationRequest req) {
        try {
            EvaluationResponse resp = RelevancyEvaluator.builder()
                    .chatClientBuilder(judgeBuilder)
                    .build()
                    .evaluate(req);
            return resp.isPass();
        } catch (Exception e) {
            log.warn("relevancy evaluator failed: {} ({})", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private Boolean runFactChecking(ChatClient.Builder judgeBuilder, EvaluationRequest req) {
        try {
            EvaluationResponse resp = FactCheckingEvaluator.builder(judgeBuilder).build().evaluate(req);
            return resp.isPass();
        } catch (Exception e) {
            log.warn("fact-checking evaluator failed: {} ({})", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private ChatClient.Builder tryJudgeChatClientBuilder() {
        ChatModel chatModel = llmConfigService.activeChatModel();
        return chatModel == null ? null : ChatClient.builder(chatModel);
    }

    /** 把两个 boolean 评分映射成 0~100 数值分。relevancy 占大头(决定相关性),fact-checking 起扣分作用。 */
    private static Integer computeScore(Boolean relevancy, Boolean factChecking) {
        if (relevancy == null) {
            return null;
        }
        if (!relevancy) {
            return 0;
        }
        if (factChecking == null) {
            return 100;
        }
        return factChecking ? 100 : 60;
    }

    /**
     * **只**返回需要让用户关注的"问题"项 —— 全 PASS 时返回空 list,避免前端 failReasons section
     * 错误地展示"PASS"反馈。evaluator 不可用 / FAIL 时才会有内容。
     */
    private static List<String> buildFeedback(Boolean relevancy, Boolean factChecking) {
        if (relevancy == null) {
            return List.of("semantic relevancy: unavailable (judge LLM error)");
        }
        if (!relevancy) {
            return List.of("semantic relevancy: FAIL — response not aligned with the query/context");
        }
        if (Boolean.FALSE.equals(factChecking)) {
            return List.of(
                    "semantic fact-checking: FAIL — response contains claims not supported by retrieved context (hallucination risk)");
        }
        // relevancy=PASS, factChecking=PASS 或 N/A → 没有问题项,空 list
        return List.of();
    }

    /**
     * 评估结果。{@code score=null} 表示 evaluator 不可用(主流程失败 / 无 LLM / 全部 fail-safe 触发)。
     */
    @Data
    @AllArgsConstructor
    public static class Verdict {

        private Boolean relevancyPass;
        private Boolean factCheckingPass;
        private Integer score;
        private List<String> feedback;

        public boolean isAvailable() {
            return score != null;
        }

        static Verdict unavailable(String reason) {
            return new Verdict(null, null, null, List.of("evaluator unavailable: " + reason));
        }
    }
}
