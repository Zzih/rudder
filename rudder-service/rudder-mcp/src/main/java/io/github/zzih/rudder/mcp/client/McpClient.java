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

package io.github.zzih.rudder.mcp.client;

/**
 * 内置的 MCP 客户端文档列表。
 *
 * <p>每项对应 classpath 下一份 markdown:{@code spi-guide/mcp-client-<id>.<lang>.md}。
 * 新增客户端只需添加枚举值 + markdown 文件,前端零改动。
 */
public enum McpClient {

    CLAUDE_DESKTOP("claude-desktop", "Claude Desktop", "orange"),
    CURSOR("cursor", "Cursor", "purple"),
    INSPECTOR("inspector", "MCP Inspector", "teal");

    /** 文件名片段(也是前端展示的稳定 id),不含 type 前缀。 */
    private final String id;
    /** 用于前端卡片标题。 */
    private final String label;
    /** 前端 logo 圆点取色键(对齐 admin.scss 的 --r-orange/-purple/-teal)。 */
    private final String color;

    McpClient(String id, String label, String color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String color() {
        return color;
    }
}
