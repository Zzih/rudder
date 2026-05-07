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
import java.util.Optional;

/**
 * 管理所有已配置 MCP server 的连接池。
 * 负责启动 / 重连 / 健康检查 / 工具枚举。
 */
public interface McpClientManager {

    /** 根据配置建立或复用连接,连不上抛异常。 */
    McpClient getOrCreate(McpServerConfig config) throws Exception;

    /** 关闭指定 server 的连接。 */
    void close(String serverName);

    /** 工作区维度可见的所有工具(含平台级 + 工作区级配置) */
    List<McpToolDescriptor> listAvailableTools(Long workspaceId);

    /** 健康检查,更新 t_r_ai_mcp_server.health_status。 */
    void refreshHealth();

    /**
     * 已缓存的连接查询(typical caller:ToolRegistry 在调用 listAvailableTools 后 execute 工具时复用)。
     * 默认空实现,真实 provider 覆盖。
     */
    default Optional<McpClient> getCachedClient(String serverName) {
        return Optional.empty();
    }
}
