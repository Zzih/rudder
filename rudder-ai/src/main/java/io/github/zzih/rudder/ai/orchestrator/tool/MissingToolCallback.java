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

package io.github.zzih.rudder.ai.orchestrator.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import lombok.extern.slf4j.Slf4j;

/**
 * 兜底 ToolCallback:当 LLM 幻觉出一个不存在的工具名时(例如把 run_sql_readonly 写成
 * run_sql_readless),返回一段提示性错误而不是让 Spring AI 抛 IllegalStateException 炸掉
 * 整个 stream。LLM 看到这条错误后,能在下一轮自纠写出正确的名字。
 */
@Slf4j
public class MissingToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    public MissingToolCallback(String toolName) {
        this.definition = ToolDefinition.builder()
                .name(toolName)
                .description("Fallback for unknown / hallucinated tool name")
                .inputSchema("{\"type\":\"object\"}")
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        String name = definition.name();
        log.warn("LLM called non-existent tool '{}' — returning error hint to let it self-correct", name);
        return "{\"error\":\"Tool '" + name + "' does not exist. Double-check the tool name spelling and retry "
                + "with the correct tool. Do NOT invent tool names.\"}";
    }
}
