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
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/** MCP server 连接配置(运行时视图,与 t_r_ai_mcp_server 表对应)。 */
@Data
@Builder
public class McpServerConfig {

    private Long id;
    private String name;

    private McpTransport transport;

    /** STDIO 时的启动命令,含参数如 "npx -y @modelcontextprotocol/server-filesystem /data"。 */
    private String command;

    /** HTTP_SSE 时的地址。 */
    private String url;

    /** 启动环境变量(含可能的 token)。 */
    private Map<String, String> env;

    /** 允许的工具列表,null=全允许。 */
    private List<String> toolAllowlist;

    private boolean enabled;
}
