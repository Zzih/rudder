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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG advisor 链路 debug trace —— 把每个 stage 的 input / output / 耗时 / 错误暴露给 admin UI。
 * <p>
 * 与生产 RAG 链路解耦:debug 端点手动跑一遍各 stage 收集 trace,**不复用** Spring AI advisor chain
 * (advisor 是黑盒,不暴露 hook)。配置从 {@code RagPipelineConfigService.active()} 读,
 * 保证 debug 看到的就是生产正在用的链路。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDebugTrace {

    /** 用户原始 query。 */
    private String originalQuery;

    /** 各阶段的 trace。按执行顺序排列。 */
    @Builder.Default
    private List<StageTrace> stages = new ArrayList<>();

    /** 最终拼给 LLM 的 system + user 完整 prompt(经 ContextualQueryAugmenter)。 */
    private String finalPrompt;

    /** 是否真启用了 RAG。false 时 stages 只含 originalQuery,其它阶段都跳过 */
    private boolean ragEnabled;

    /** 整体耗时(毫秒)。 */
    private Integer totalLatencyMs;

    /** Pipeline 设置快照(回显给 admin 看当前生产配置)。 */
    private Map<String, Object> pipelineSnapshot;

    /**
     * 单个 stage 的 trace。{@code skipped=true} 表示配置开关关或前置条件不满足跳过该阶段;
     * {@code error} 非空表示该阶段 fail-safe 触发。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageTrace {

        /** 阶段名,如 "compression" / "translation" / "rewrite" / "multi-query" / "retrieval" / "rerank" / "augmenter"。 */
        private String name;

        /** 是否跳过(配置关/不满足前置条件)。 */
        private boolean skipped;

        /** 跳过原因(skipped=true 时填),如 "disabled in pipeline" / "no provider configured"。 */
        private String skipReason;

        /** 阶段耗时(毫秒)。skipped 时为 0。 */
        private Integer durationMs;

        /** 阶段失败时的错误信息(fail-safe 后退回原值)。 */
        private String error;

        /** 阶段输入(human-readable,如 query 文本 / 候选 chunk 列表预览)。 */
        private Object input;

        /** 阶段输出(human-readable)。 */
        private Object output;

        public static StageTrace skipped(String name, String reason) {
            return StageTrace.builder()
                    .name(name)
                    .skipped(true)
                    .skipReason(reason)
                    .durationMs(0)
                    .build();
        }
    }
}
