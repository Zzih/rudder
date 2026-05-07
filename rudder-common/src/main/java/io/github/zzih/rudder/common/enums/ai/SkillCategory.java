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

package io.github.zzih.rudder.common.enums.ai;

import java.util.Locale;

/**
 * AI Skill 分类。持久化到 {@code t_r_ai_skill.category} VARCHAR 列。
 * <p>
 * 前端 skill 下拉用这个枚举做分类 tag / 筛选。未知或空值 → {@link #OTHER} 兜底。
 */
public enum SkillCategory {

    /** 根据需求生成代码(SQL / Python / Flink / Shell 等)。 */
    CODE_GEN,

    /** 错误诊断:读日志、分析失败执行、定位根因。 */
    DEBUG,

    /** 优化:重写 SQL、性能调优、代码质量改进。 */
    OPTIMIZE,

    /** 解释:讲解一段代码、方言差异、概念定义。 */
    EXPLAIN,

    /** 评审:代码 review、风险检查、合规性审查。 */
    REVIEW,

    /** 分析:跑数、对比结果、生成报告。 */
    ANALYZE,

    /** 数据探索:找表、理解 schema、搜索业务语义。 */
    DATA_DISCOVERY,

    /** 运维:调度 / 上线 / 资源 / 环境管理。 */
    OPS,

    /** 其他:不属于以上类别的 skill。 */
    OTHER;

    /** 大小写 / 空白容错,未知值 → OTHER。 */
    public static SkillCategory from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return SkillCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
