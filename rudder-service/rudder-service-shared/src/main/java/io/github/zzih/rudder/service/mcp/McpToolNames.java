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

/**
 * MCP 工具命名约定 {@code mcp:{server}.{tool}} 的共享常量 / 拼装 / 拆解工具。
 * ToolRegistry / McpToolAdapter / PermissionGate / RudderToolCallback 共享用。
 */
public final class McpToolNames {

    public static final String PREFIX = "mcp:";

    private McpToolNames() {
    }

    public static String compose(String serverName, String toolName) {
        return PREFIX + serverName + "." + toolName;
    }

    public static boolean isMcp(String llmToolName) {
        return llmToolName != null && llmToolName.startsWith(PREFIX);
    }

    /** 返回 "{server}" 或 null(不是 mcp 工具名)。 */
    public static String serverOf(String llmToolName) {
        if (!isMcp(llmToolName)) {
            return null;
        }
        int dot = llmToolName.indexOf('.', PREFIX.length());
        return dot < 0 ? null : llmToolName.substring(PREFIX.length(), dot);
    }

    /** 返回 "{tool}",若不是 mcp 名则返回原字符串(给 PermissionGate 按前缀规则判定)。 */
    public static String toolOf(String llmToolName) {
        if (llmToolName == null || !llmToolName.startsWith(PREFIX)) {
            return llmToolName;
        }
        int dot = llmToolName.indexOf('.', PREFIX.length());
        return dot < 0 ? llmToolName.substring(PREFIX.length()) : llmToolName.substring(dot + 1);
    }
}
