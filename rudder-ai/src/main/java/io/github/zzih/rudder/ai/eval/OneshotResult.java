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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次 eval 执行的结果快照。{@link EvalExecutor} 产出,{@link EvalVerifier} 消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneshotResult {

    /** LLM 最终的文本回复(不含工具轨迹)。 */
    private String finalText;

    /** 工具调用序列,顺序保留。 */
    @Builder.Default
    private List<ToolInvocation> toolCalls = new ArrayList<>();

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private String provider;

    private String model;

    /** 执行期间出现的错误信息(网络 / provider / 异常);为 null 表示执行本身成功。 */
    private String error;

    /**
     * RAG advisor 在本次 turn 中检索到并喂给 LLM 的上下文文档(从
     * {@code RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT} metadata 提取)。
     * <p>用于喂给 {@code FactCheckingEvaluator} 做幻觉检测。
     * 不走 RAG 的 case 此字段为空 list。
     */
    @Builder.Default
    private List<Document> retrievedContext = new ArrayList<>();

    public int totalTokens() {
        int p = promptTokens == null ? 0 : promptTokens;
        int c = completionTokens == null ? 0 : completionTokens;
        return p + c;
    }

    public boolean executionSucceeded() {
        return error == null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolInvocation {

        private String name;
        private JsonNode input;
        private String output;
        private boolean success;
        private String errorMessage;
        private Integer latencyMs;
    }
}
