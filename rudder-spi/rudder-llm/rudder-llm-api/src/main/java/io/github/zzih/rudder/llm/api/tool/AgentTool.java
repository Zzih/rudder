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

package io.github.zzih.rudder.llm.api.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 可调用的工具契约。
 * 所有内置(NATIVE)工具实现此接口并注册到 {@code ToolRegistry}。
 */
public interface AgentTool {

    /** 唯一名,供 LLM 引用。符合 `[a-z_][a-z0-9_]*`。 */
    String name();

    /** LLM 读到的工具说明(英文,影响调用质量)。 */
    String description();

    /** 参数的 JSON Schema,供 LLM 生成入参。 */
    JsonNode inputSchema();

    /**
     * 执行工具。input 是 LLM 提供的参数(已按 schema 校验)。
     * 返回值建议是 JSON string 或短 markdown,AI 会把它接回对话。
     */
    String execute(JsonNode input, ToolExecutionContext ctx) throws Exception;

    /** 所属来源,用于审计 / UI 展示。 */
    default ToolSource source() {
        return ToolSource.NATIVE;
    }

    enum ToolSource {
        NATIVE,
        SKILL,
        MCP
    }
}
