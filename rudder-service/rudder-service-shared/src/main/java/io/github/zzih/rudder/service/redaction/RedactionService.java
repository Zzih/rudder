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

package io.github.zzih.rudder.service.redaction;

import io.github.zzih.rudder.common.model.ColumnMeta;

import java.util.List;
import java.util.Map;

/**
 * 平台级脱敏服务 —— 所有数据出口的统一入口。
 * <p>
 * 规则体系由 admin 在 {@code t_r_redaction_rule} + {@code t_r_redaction_strategy} 管理。
 * 三类规则(TAG / COLUMN / TEXT)按优先级短路:
 * <ul>
 *   <li>结构化数据:TAG → COLUMN → TEXT(仅字符串值)</li>
 *   <li>自由文本:TEXT</li>
 * </ul>
 * 规则本身不带 scope / workspace 维度,一次配置全局生效。
 */
public interface RedactionService {

    /**
     * 对一行 Map(column-name → value)的所有列按 ColumnMeta 应用脱敏。
     * 原地改写并返回同一 list,便于调用方 short-circuit。Sink 主路径直接调它。
     */
    List<Map<String, Object>> applyMapRows(List<ColumnMeta> columns, List<Map<String, Object>> rows);

    /**
     * 对单个值应用脱敏。用于场景里按 cell 调用,不建议在热路径高频用。
     */
    Object applyValue(ColumnMeta column, Object value);

    /**
     * 对自由文本(AI 输出 / 日志 / tool IO 等)按 TEXT 规则脱敏。
     * 无命中返回原文,永远不抛。
     */
    String scrubText(String text);

    /**
     * admin CUD 规则 / 策略后调用,清缓存让下次读穿到 DB。
     */
    default void refreshRules() {
        // no-op
    }

    /**
     * 管理端实时试跑:把策略配置应用到 sample,可选 {@code rulePattern}(TEXT 规则场景)
     * 先用 pattern 找 PII 片段,再按策略替换命中部分。不落库。
     */
    String testApply(StrategySpec strategy, String rulePattern, String sample);
}
