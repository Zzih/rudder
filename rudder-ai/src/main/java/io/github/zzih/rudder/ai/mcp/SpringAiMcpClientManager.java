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

package io.github.zzih.rudder.ai.mcp;

import io.github.zzih.rudder.ai.dto.AiMcpServerDTO;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiMcpServerDao;
import io.github.zzih.rudder.dao.entity.AiMcpServer;
import io.github.zzih.rudder.service.mcp.McpClient;
import io.github.zzih.rudder.service.mcp.McpClientManager;
import io.github.zzih.rudder.service.mcp.McpServerConfig;
import io.github.zzih.rudder.service.mcp.McpToolDescriptor;
import io.github.zzih.rudder.service.mcp.McpTransport;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Spring AI MCP 客户端 starter 下层 MCP Java SDK 的 McpClientManager 唯一实现。
 * <ul>
 *   <li>STDIO 传输:走 {@link StdioClientTransport} + {@link ServerParameters},适合本地子进程 MCP server</li>
 *   <li>HTTP_SSE 传输:走 {@link HttpClientSseClientTransport},适合远端 MCP server</li>
 * </ul>
 * MCP 不是 provider-swap 式 SPI(只有这一种实现),所以本类住在 service-server 业务层,
 * 不走独立 SPI 模块。McpClient / McpClientManager 接口仍放在 rudder-mcp-api 作为契约。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiMcpClientManager implements McpClientManager {

    private final AiMcpServerDao mcpServerDao;
    private final ObjectMapper objectMapper;

    /** name → connected McpClient(lazy-init on first use,close 时移除)。 */
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    @Override
    public McpClient getOrCreate(McpServerConfig config) {
        return clients.computeIfAbsent(config.getName(), k -> build(config));
    }

    @Override
    public java.util.Optional<McpClient> getCachedClient(String serverName) {
        return java.util.Optional.ofNullable(clients.get(serverName));
    }

    @Override
    public synchronized void close(String serverName) {
        McpClient c = clients.remove(serverName);
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                log.debug("MCP close {} error: {}", serverName, e.getMessage());
            }
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        for (String name : new ArrayList<>(clients.keySet())) {
            close(name);
        }
    }

    @Override
    public List<McpToolDescriptor> listAvailableTools(Long workspaceId) {
        // MCP server 层不再按 workspace 过滤;工作区可见性全在 t_r_ai_tool_config 决定。
        // workspaceId 参数保留接口签名兼容性,本实现忽略。
        List<McpToolDescriptor> out = new ArrayList<>();
        for (AiMcpServer s : enabledServers()) {
            try {
                McpClient client = getOrCreate(toConfig(s));
                client.connect();
                out.addAll(filterAllowed(client.listTools(), parseList(s.getToolAllowlist())));
            } catch (Exception e) {
                log.warn("list tools for MCP server {} failed: {}", s.getName(), e.getMessage());
            }
        }
        return out;
    }

    @Override
    public void refreshHealth() {
        for (AiMcpServer s : mcpServerDao.selectEnabled()) {
            boolean healthy;
            try {
                healthy = getOrCreate(toConfig(s)).isHealthy();
            } catch (Exception e) {
                healthy = false;
            }
            AiMcpServer update = new AiMcpServer();
            update.setId(s.getId());
            update.setHealthStatus(healthy ? "UP" : "DOWN");
            update.setLastHealthAt(LocalDateTime.now());
            mcpServerDao.updateById(update);
        }
    }

    // ==================== admin CRUD(供 Controller 调用,避开 Controller→Mapper 跨层) ====================

    public com.baomidou.mybatisplus.core.metadata.IPage<AiMcpServer> page(int pageNum, int pageSize) {
        return mcpServerDao.selectPage(pageNum, pageSize);
    }

    public AiMcpServer create(AiMcpServer body) {
        if (body.getHealthStatus() == null) {
            body.setHealthStatus("UNKNOWN");
        }
        mcpServerDao.insert(body);
        return body;
    }

    public void update(Long id, AiMcpServer body) {
        body.setId(id);
        mcpServerDao.updateById(body);
    }

    public void delete(Long id) {
        AiMcpServer existing = mcpServerDao.selectById(id);
        mcpServerDao.deleteById(id);
        if (existing != null && existing.getName() != null) {
            close(existing.getName());
        }
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public com.baomidou.mybatisplus.core.metadata.IPage<AiMcpServerDTO> pageDetail(int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(page(pageNum, pageSize), AiMcpServerDTO.class);
    }

    public AiMcpServerDTO createDetail(AiMcpServerDTO body) {
        AiMcpServer entity = BeanConvertUtils.convert(body, AiMcpServer.class);
        return BeanConvertUtils.convert(create(entity), AiMcpServerDTO.class);
    }

    public void updateDetail(Long id, AiMcpServerDTO body) {
        update(id, BeanConvertUtils.convert(body, AiMcpServer.class));
    }

    // ==================== internal ====================

    /** 返回所有启用的 MCP server(workspace 归属已迁移到 t_r_ai_tool_config,此处不再过滤)。 */
    private List<AiMcpServer> enabledServers() {
        return mcpServerDao.selectEnabled();
    }

    private McpServerConfig toConfig(AiMcpServer s) {
        return McpServerConfig.builder()
                .id(s.getId())
                .name(s.getName())
                .transport(McpTransport.valueOf(s.getTransport()))
                .command(s.getCommand())
                .url(s.getUrl())
                .env(parseMap(s.getEnv()))
                .toolAllowlist(parseList(s.getToolAllowlist()))
                .enabled(s.getEnabled() != null && s.getEnabled())
                .build();
    }

    private McpClient build(McpServerConfig config) {
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        McpClientTransport transport = (config.getTransport() == McpTransport.STDIO)
                ? buildStdio(config, jsonMapper)
                : buildSse(config);
        McpSyncClient sync = io.modelcontextprotocol.client.McpClient
                .sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        return new SpringAiMcpClient(config.getName(), sync, objectMapper);
    }

    private McpClientTransport buildStdio(McpServerConfig config, McpJsonMapper jsonMapper) {
        String cmdLine = config.getCommand();
        if (cmdLine == null || cmdLine.isBlank()) {
            throw new IllegalArgumentException("STDIO transport requires 'command'");
        }
        String[] parts = cmdLine.trim().split("\\s+");
        String cmd = parts[0];
        List<String> args = parts.length > 1 ? Arrays.asList(parts).subList(1, parts.length) : List.of();
        ServerParameters params = ServerParameters.builder(cmd)
                .args(args)
                .env(config.getEnv() != null ? new HashMap<>(config.getEnv()) : new HashMap<>())
                .build();
        return new StdioClientTransport(params, jsonMapper);
    }

    private McpClientTransport buildSse(McpServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP_SSE transport requires 'url'");
        }
        return HttpClientSseClientTransport.builder(config.getUrl()).build();
    }

    private List<McpToolDescriptor> filterAllowed(List<McpToolDescriptor> tools, List<String> allow) {
        if (allow == null || allow.isEmpty()) {
            return tools;
        }
        List<McpToolDescriptor> out = new ArrayList<>();
        for (McpToolDescriptor t : tools) {
            if (allow.contains(t.getToolName())) {
                out.add(t);
            }
        }
        return out;
    }

    private Map<String, String> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
