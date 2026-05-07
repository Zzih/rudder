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

package io.github.zzih.rudder.llm.api.skill;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;

/**
 * Skill 定义(runtime 视图)。所有 skill 都存 {@code t_r_ai_skill} 表(纯定义,无工作区归属);
 * 工作区可见性 + 权限规则由 {@code t_r_ai_tool_config} 里 {@code skill__<name>} 记录决定。
 */
@Data
@Builder
public class SkillDefinition {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String category;

    /** Prompt 模板(system prompt 原文)。 */
    private String promptTemplate;

    /** 入参 JSON Schema。 */
    private JsonNode inputSchema;

    /** 该 skill 运行时需要的工具名列表。 */
    private List<String> requiredTools;

    /** 可选覆盖模型。 */
    private String modelOverride;

    /** 可选步骤序列(声明式 skill);空则按自由 prompt 执行。 */
    private List<Step> steps;

    private boolean enabled;

    @Data
    @Builder
    public static class Step {

        /** TOOL | PROMPT | OUTPUT_TEMPLATE */
        private StepType type;
        private String toolName;
        private String prompt;
        private String outputTemplate;
    }

    public enum StepType {
        TOOL,
        PROMPT,
        OUTPUT_TEMPLATE
    }
}
