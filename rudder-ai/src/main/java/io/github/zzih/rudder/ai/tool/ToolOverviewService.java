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

package io.github.zzih.rudder.ai.tool;

import io.github.zzih.rudder.ai.permission.ToolConfigService;
import io.github.zzih.rudder.ai.skill.SkillAgentTool;
import io.github.zzih.rudder.ai.skill.SkillRegistry;
import io.github.zzih.rudder.dao.entity.AiToolConfig;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.AgentTool.ToolSource;
import io.github.zzih.rudder.service.mcp.McpClientManager;
import io.github.zzih.rudder.service.mcp.McpToolDescriptor;
import io.github.zzih.rudder.service.mcp.McpToolNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 聚合 admin 侧"全部可调 tool"的只读视图:NATIVE + MCP + SKILL。每条附带
 * 由 {@link PermissionGate} 算出的代码默认权限 + 从 {@code t_r_ai_tool_config} 读到的 config
 * (含 workspaceIds + minRole/requireConfirm/readOnly/enabled 覆盖)。
 * <p>
 * 仅供 Tools 总览页 / Skills 选择器用,不参与 runtime tool 调用流程。
 */
@Service
@RequiredArgsConstructor
public class ToolOverviewService {

    private final ToolRegistry toolRegistry;
    private final McpClientManager mcpClientManager;
    private final SkillRegistry skillRegistry;
    private final PermissionGate permissionGate;
    private final ToolConfigService toolConfigService;

    public List<ToolView> list(ToolSource sourceFilter, boolean excludeSkill) {
        // admin 总览不按 workspace 过滤,直接把整个工具池 + 所有 config 行一次拉出来
        Map<String, AiToolConfig> configByTool = new HashMap<>();
        for (AiToolConfig c : toolConfigService.listAll()) {
            configByTool.put(c.getToolName(), c);
        }

        List<ToolView> out = new ArrayList<>();

        if (sourceFilter == null || sourceFilter == ToolSource.NATIVE) {
            for (AgentTool t : toolRegistry.allNative()) {
                out.add(build(t.name(), t.source(), t.description(), t.inputSchema(), configByTool));
            }
        }
        if (sourceFilter == null || sourceFilter == ToolSource.MCP) {
            // MCP 层不再按 workspace 过滤,传 null 拿所有启用 server 的 tool
            for (McpToolDescriptor d : safeListMcp()) {
                String fullName = McpToolNames.compose(d.getServerName(), d.getToolName());
                out.add(build(fullName, ToolSource.MCP, d.getDescription(), d.getInputSchema(), configByTool));
            }
        }
        if (!excludeSkill && (sourceFilter == null || sourceFilter == ToolSource.SKILL)) {
            for (var skill : skillRegistry.listEnabled()) {
                String fullName = SkillAgentTool.NAME_PREFIX + skill.getName();
                String desc = skill.getDescription() == null ? skill.getDisplayName() : skill.getDescription();
                out.add(build(fullName, ToolSource.SKILL, desc, skill.getInputSchema(), configByTool));
            }
        }
        return out;
    }

    // ==================== helpers ====================

    private ToolView build(String name, ToolSource source, String description, JsonNode schema,
                           Map<String, AiToolConfig> configByTool) {
        AiToolConfig cfg = configByTool.get(name);
        return ToolView.builder()
                .name(name)
                .source(source)
                .description(description)
                .inputSchema(schema)
                .defaultPermission(defaultPermission(name))
                .config(toConfigView(cfg))
                .build();
    }

    private PermissionDefault defaultPermission(String toolName) {
        return PermissionDefault.builder()
                .minRole(permissionGate.requiredRoleFor(toolName).name())
                .requireConfirm(permissionGate.requiresConfirm(toolName))
                .readOnly(permissionGate.allowedInReadOnly(toolName))
                .build();
    }

    private ConfigView toConfigView(AiToolConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return ConfigView.builder()
                .id(cfg.getId())
                .workspaceIds(ToolConfigService.parseWorkspaceIds(cfg.getWorkspaceIds()))
                .minRole(cfg.getMinRole())
                .requireConfirm(cfg.getRequireConfirm())
                .readOnly(cfg.getReadOnly())
                .enabled(cfg.getEnabled())
                .build();
    }

    private List<McpToolDescriptor> safeListMcp() {
        try {
            return mcpClientManager.listAvailableTools(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== DTOs ====================

    @Data
    @Builder
    public static class ToolView {

        private String name;
        private ToolSource source;
        private String description;
        private JsonNode inputSchema;
        private PermissionDefault defaultPermission;
        /** null = 无 config 行,走默认规则,所有 workspace 可见。 */
        private ConfigView config;
    }

    @Data
    @Builder
    public static class PermissionDefault {

        private String minRole;
        private boolean requireConfirm;
        private boolean readOnly;
    }

    @Data
    @Builder
    public static class ConfigView {

        private Long id;
        /** null = 所有 workspace;非空 = 指定 workspace id 列表。 */
        private List<Long> workspaceIds;
        private String minRole;
        private Boolean requireConfirm;
        private Boolean readOnly;
        private Boolean enabled;
    }
}
