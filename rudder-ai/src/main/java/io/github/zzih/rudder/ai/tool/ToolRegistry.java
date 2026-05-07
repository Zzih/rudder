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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.AiToolConfig;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.service.mcp.McpClient;
import io.github.zzih.rudder.service.mcp.McpClientManager;
import io.github.zzih.rudder.service.mcp.McpToolDescriptor;
import io.github.zzih.rudder.service.mcp.McpToolNames;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * 汇聚所有 Agent 可调用的工具:
 * <ul>
 *   <li>内置 NATIVE 工具:Spring 容器里所有 {@link AgentTool} bean,启动时固定</li>
 *   <li>MCP 工具:{@link McpClientManager#listAvailableTools(Long)} 动态枚举,按 workspace 过滤</li>
 * </ul>
 * 工具名唯一。MCP 名字形如 {@code mcp:{server}.{tool}},天然与 NATIVE 工具隔离。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> nativeByName = new HashMap<>();
    private final McpClientManager mcpClientManager;
    private final ToolConfigService toolConfigService;

    public ToolRegistry(List<AgentTool> tools, McpClientManager mcpClientManager, ToolConfigService toolConfigService) {
        this.mcpClientManager = mcpClientManager;
        this.toolConfigService = toolConfigService;
        for (AgentTool tool : tools) {
            if (nativeByName.containsKey(tool.name())) {
                throw new IllegalStateException("Duplicate agent tool: " + tool.name());
            }
            nativeByName.put(tool.name(), tool);
        }
        log.info("registered {} native agent tools: {}", nativeByName.size(), nativeByName.keySet());
    }

    // ==================== Lookup ====================

    /** 查工具:先查 NATIVE,找不到再尝试 MCP(按名字前缀 {@code mcp:} 判定)。 */
    public Optional<AgentTool> find(String name, Long workspaceId) {
        AgentTool t = nativeByName.get(name);
        if (t != null) {
            return Optional.of(t);
        }
        if (McpToolNames.isMcp(name)) {
            return findMcp(name, workspaceId);
        }
        return Optional.empty();
    }

    /** 老签名,保留给非 workspace 上下文(只查 NATIVE)。 */
    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(nativeByName.get(name));
    }

    public List<AgentTool> allNative() {
        return List.copyOf(nativeByName.values());
    }

    /**
     * 聚合 NATIVE + workspace 可见 MCP 工具。MCP 工具名为 {@code mcp:{server}.{tool}}。
     * 返回值给 AgentExecutor 包装成 Spring AI {@code ToolCallback}。
     */
    public List<AgentTool> allForWorkspace(Long workspaceId) {
        return allForWorkspace(workspaceId, null);
    }

    /**
     * 同 {@link #allForWorkspace(Long)},但按 {@code allowedNames} 白名单过滤。
     * null 或空集合 = 不过滤;否则只返回名字命中的工具(skill 的 requiredTools 走这条)。
     */
    public List<AgentTool> allForWorkspace(Long workspaceId, Collection<String> allowedNames) {
        boolean filter = allowedNames != null && !allowedNames.isEmpty();
        Set<String> allow = filter ? new HashSet<>(allowedNames) : null;
        List<AgentTool> out = new ArrayList<>();
        for (AgentTool t : nativeByName.values()) {
            if (filter && !allow.contains(t.name())) {
                continue;
            }
            if (isHiddenForWorkspace(t.name(), workspaceId)) {
                continue;
            }
            out.add(t);
        }
        for (McpToolDescriptor desc : safeListMcp(workspaceId)) {
            String name = McpToolNames.compose(desc.getServerName(), desc.getToolName());
            if (filter && !allow.contains(name)) {
                continue;
            }
            if (isHiddenForWorkspace(name, workspaceId)) {
                continue;
            }
            Optional<McpClient> client = mcpClientManager.getCachedClient(desc.getServerName());
            client.ifPresent(c -> out.add(new McpToolAdapter(desc, c)));
        }
        return out;
    }

    /** 查 tool_config:对该 workspace 生效 + enabled=false → 隐藏此 tool。 */
    private boolean isHiddenForWorkspace(String toolName, Long workspaceId) {
        try {
            AiToolConfig cfg = toolConfigService.find(toolName, workspaceId);
            return cfg != null && Boolean.FALSE.equals(cfg.getEnabled());
        } catch (Exception e) {
            log.debug("tool config lookup failed for {}: {}", toolName, e.getMessage());
            return false;
        }
    }

    // ==================== Provider schema ====================

    /** 组装给 LLM 的 tools 数组(NATIVE + 当前 workspace 可见的 MCP)。 */
    public ArrayNode toProviderSchema(Long workspaceId) {
        ArrayNode array = JsonUtils.createArrayNode();
        for (AgentTool tool : nativeByName.values()) {
            appendSchema(array, tool.name(), tool.description(), tool.inputSchema());
        }
        for (McpToolDescriptor desc : safeListMcp(workspaceId)) {
            String name = McpToolNames.compose(desc.getServerName(), desc.getToolName());
            appendSchema(array, name, desc.getDescription(), desc.getInputSchema());
        }
        return array;
    }

    /** 老签名:仅 NATIVE,用于非 turn 场景(Eval / 诊断等)。 */
    public ArrayNode toProviderSchema() {
        ArrayNode array = JsonUtils.createArrayNode();
        for (AgentTool tool : nativeByName.values()) {
            appendSchema(array, tool.name(), tool.description(), tool.inputSchema());
        }
        return array;
    }

    // ==================== internal ====================

    private void appendSchema(ArrayNode array, String name, String description, JsonNode schema) {
        ObjectNode node = array.addObject();
        node.put("name", name);
        node.put("description", description == null ? "" : description);
        if (schema != null) {
            node.set("input_schema", schema);
        }
    }

    private List<McpToolDescriptor> safeListMcp(Long workspaceId) {
        try {
            return mcpClientManager.listAvailableTools(workspaceId);
        } catch (Exception e) {
            log.warn("list MCP tools for workspace {} failed: {}", workspaceId, e.getMessage());
            return List.of();
        }
    }

    private Optional<AgentTool> findMcp(String fullName, Long workspaceId) {
        String serverName = McpToolNames.serverOf(fullName);
        String toolName = McpToolNames.toolOf(fullName);
        if (serverName == null) {
            return Optional.empty();
        }
        McpToolDescriptor match = null;
        for (McpToolDescriptor d : safeListMcp(workspaceId)) {
            if (serverName.equals(d.getServerName()) && toolName.equals(d.getToolName())) {
                match = d;
                break;
            }
        }
        if (match == null) {
            return Optional.empty();
        }
        // listAvailableTools 已经把 client 放进 manager 的缓存。
        Optional<McpClient> client = mcpClientManager.getCachedClient(serverName);
        if (client.isEmpty()) {
            log.warn("MCP client {} not found in cache — was listAvailableTools called first?", serverName);
            return Optional.empty();
        }
        return Optional.of(new McpToolAdapter(match, client.get()));
    }
}
