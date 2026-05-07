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

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测断言 spec。从 {@code t_r_ai_eval_case.expected_json} 解析出来,驱动
 * {@link EvalVerifier} 对 {@link OneshotResult} 做多维度校验。
 *
 * <p>JSON 示例:
 * <pre>
 *   {
 *     "sqlPattern": "SELECT.*FROM.*",
 *     "mustContain": ["GROUP BY"],
 *     "mustNotContain": ["DROP"],
 *     "mustCallTools": ["list_tables"],
 *     "mustNotCallTools": ["delete_table"],
 *     "minToolCalls": 1,
 *     "maxToolCalls": 5,
 *     "maxLatencyMs": 30000,
 *     "maxTokens": 4000
 *   }
 * </pre>
 *
 * <p>所有字段可选;全空 → 任何执行成功的 case 都 pass(仅校验"没 error")。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpectedSpec {

    // ==================== 文本断言 ====================

    /** 正则(大小写不敏感,DOTALL),要求 final text 匹配。 */
    private String sqlPattern;

    /** final text 必须全部包含(不区分大小写)。 */
    private List<String> mustContain;

    /** final text 必须都不包含。 */
    private List<String> mustNotContain;

    // ==================== 工具断言 ====================

    /** 调用序列中必须出现(去重比较,顺序不敏感)。 */
    private List<String> mustCallTools;

    /** 调用序列中禁止出现。 */
    private List<String> mustNotCallTools;

    /** 工具调用至少次数(含重复)。 */
    private Integer minToolCalls;

    /** 工具调用最多次数。 */
    private Integer maxToolCalls;

    // ==================== 性能断言 ====================

    /** 总耗时上限(ms)。 */
    private Integer maxLatencyMs;

    /** 总 token 上限(promptTokens + completionTokens)。 */
    private Integer maxTokens;

    public static ExpectedSpec parse(String json) {
        if (json == null || json.isBlank()) {
            return new ExpectedSpec();
        }
        try {
            return JsonUtils.fromJson(json, ExpectedSpec.class);
        } catch (Exception e) {
            return new ExpectedSpec();
        }
    }
}
