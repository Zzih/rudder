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

package io.github.zzih.rudder.ai.skill;

import io.github.zzih.rudder.ai.tool.ToolRegistry;
import io.github.zzih.rudder.llm.api.skill.SkillDefinition;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.service.config.LlmConfigService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 把 workspace 可见的 enabled skill 变成 {@link AgentTool} 列表,供 AgentExecutor 追加到
 * 父 agent 的 tool 集里。LLM 看到这些 {@code skill__xxx} 工具后自行决策何时调用。
 */
@Component
@RequiredArgsConstructor
public class SkillToolProvider {

    private final SkillRegistry skillRegistry;
    private final LlmConfigService llmConfigService;
    private final ToolRegistry toolRegistry;

    public List<AgentTool> buildFor(long workspaceId, SkillInvocationContext invocation) {
        // Skill 不再按 workspace 过滤(工作区可见性交给 tool_config);这里拿所有启用 skill,
        // ToolRegistry/SkillAgentTool 下游自会按 tool_config 隐藏/保留。
        List<SkillDefinition> enabled = skillRegistry.listEnabled();
        List<AgentTool> out = new ArrayList<>(enabled.size());
        for (SkillDefinition skill : enabled) {
            out.add(new SkillAgentTool(skill, llmConfigService, toolRegistry, invocation));
        }
        return out;
    }
}
