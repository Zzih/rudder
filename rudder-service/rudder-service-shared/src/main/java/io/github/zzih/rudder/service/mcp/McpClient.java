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

package io.github.zzih.rudder.service.mcp;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单个 MCP server 的客户端。
 * 生命周期由 {@link McpClientManager} 管理。
 */
public interface McpClient extends AutoCloseable {

    /** 连接远端 server(stdio: 启动子进程;http-sse: 建连)。 */
    void connect() throws Exception;

    /** 枚举该 server 提供的工具。 */
    List<McpToolDescriptor> listTools() throws Exception;

    /**
     * 调用一个工具。input 是 JSON-Schema 校验过的参数对象。
     */
    McpToolResult callTool(String toolName, JsonNode input) throws Exception;

    /** 轻量健康检查(ping)。 */
    boolean isHealthy();

    @Override
    void close();
}
