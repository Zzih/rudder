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

import io.github.zzih.rudder.service.mcp.McpClient;
import io.github.zzih.rudder.service.mcp.McpToolDescriptor;
import io.github.zzih.rudder.service.mcp.McpToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

/** MCP 连接封装:Rudder 的 McpClient SPI 委托给官方 MCP Java SDK 的 {@link McpSyncClient}。 */
@Slf4j
class SpringAiMcpClient implements McpClient {

    private final String serverName;
    private final McpSyncClient delegate;
    private final ObjectMapper objectMapper;

    SpringAiMcpClient(String serverName, McpSyncClient delegate, ObjectMapper objectMapper) {
        this.serverName = serverName;
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void connect() {
        delegate.initialize();
    }

    @Override
    public List<McpToolDescriptor> listTools() {
        McpSchema.ListToolsResult res = delegate.listTools();
        List<McpToolDescriptor> out = new ArrayList<>();
        if (res == null || res.tools() == null) {
            return out;
        }
        for (McpSchema.Tool t : res.tools()) {
            out.add(McpToolDescriptor.builder()
                    .serverName(serverName)
                    .toolName(t.name())
                    .description(t.description())
                    .inputSchema(schemaToJson(t.inputSchema()))
                    .build());
        }
        return out;
    }

    @Override
    public McpToolResult callTool(String toolName, JsonNode input) {
        Map<String, Object> args = jsonToMap(input);
        McpSchema.CallToolRequest req = new McpSchema.CallToolRequest(toolName, args);
        try {
            McpSchema.CallToolResult callResult = delegate.callTool(req);
            boolean isError = Boolean.TRUE.equals(callResult.isError());
            String content = extractText(callResult);
            return McpToolResult.builder()
                    .content(content)
                    .success(!isError)
                    .errorMessage(isError ? content : null)
                    .build();
        } catch (Exception e) {
            log.warn("MCP tool call {}:{} failed: {}", serverName, toolName, e.getMessage());
            return McpToolResult.builder()
                    .content("")
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            delegate.ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } catch (Exception e) {
            log.debug("MCP close error: {}", e.getMessage());
        }
    }

    private String extractText(McpSchema.CallToolResult res) {
        if (res == null || res.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : res.content()) {
            if (c instanceof McpSchema.TextContent tc) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    private Map<String, Object> jsonToMap(JsonNode input) {
        if (input == null || input.isNull() || input.isMissingNode()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(input, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("MCP tool input conversion failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Spring AI 2.0.0-M5 起 {@code Tool.inputSchema()} 返 {@code Map<String,Object>}。 */
    private JsonNode schemaToJson(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.valueToTree(schema);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
