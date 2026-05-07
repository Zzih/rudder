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

package io.github.zzih.rudder.mcp.client.dto;

import lombok.Data;

/**
 * Service 层的 MCP 客户端连接指南数据。
 *
 * <p>{@code description} 来自 markdown front-matter 的 {@code description} 字段;
 * {@code guide} 是 front-matter 之后的 markdown 正文 —— 由 web 层 controller 通过
 * {@code BeanConvertUtils} 映射为 {@code McpClientGuideResponse} 回前端。
 */
@Data
public class McpClientGuide {

    private String id;
    private String label;
    private String color;
    private String description;
    private String guide;
}
