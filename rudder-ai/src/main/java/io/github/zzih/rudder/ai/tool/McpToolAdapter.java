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

import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.mcp.McpClient;
import io.github.zzih.rudder.service.mcp.McpToolDescriptor;
import io.github.zzih.rudder.service.mcp.McpToolNames;
import io.github.zzih.rudder.service.mcp.McpToolResult;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 把 {@link McpToolDescriptor}(MCP server 暴露的工具)适配为 Rudder 的 {@link AgentTool}。
 * 全名 {@code mcp:{serverName}.{toolName}},保证不与内置工具冲突。
 */
public class McpToolAdapter implements AgentTool {

    private final McpToolDescriptor descriptor;
    private final McpClient client;
    private final String fullName;

    public McpToolAdapter(McpToolDescriptor descriptor, McpClient client) {
        this.descriptor = descriptor;
        this.client = client;
        this.fullName = McpToolNames.compose(descriptor.getServerName(), descriptor.getToolName());
    }

    @Override
    public String name() {
        return fullName;
    }

    @Override
    public String description() {
        return descriptor.getDescription() == null ? "" : descriptor.getDescription();
    }

    @Override
    public JsonNode inputSchema() {
        return descriptor.getInputSchema();
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) throws Exception {
        McpToolResult result = client.callTool(descriptor.getToolName(), input);
        if (result == null) {
            return "MCP tool returned null";
        }
        if (!result.isSuccess()) {
            String msg = result.getErrorMessage() != null ? result.getErrorMessage() : result.getContent();
            return "Error: " + (msg == null ? "mcp tool failed" : msg);
        }
        return result.getContent() == null ? "" : result.getContent();
    }

    @Override
    public ToolSource source() {
        return ToolSource.MCP;
    }
}
