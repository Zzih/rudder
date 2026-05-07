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

import io.github.zzih.rudder.mcp.client.dto.McpClientGuide;
import io.github.zzih.rudder.spi.api.SpiGuideFile;
import io.github.zzih.rudder.spi.api.SpiGuideLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * 加载内置 MCP 客户端连接指南。
 *
 * <p>guide 内容存放在 classpath {@code spi-guide/mcp-client-<id>.<lang>.md},复用 SPI 框架那套
 * markdown loader —— 项目内只此一处 markdown 资源加载约定,不再重复造轮子。
 */
@Service
public class McpClientGuideService {

    /** SpiGuideLoader 的 family 段(会归一为小写 + 把下划线替换为连字符)。 */
    static final String FAMILY = "mcp-client";

    public List<McpClientGuide> listAll(Locale locale) {
        return Arrays.stream(McpClient.values())
                .map(c -> toGuide(c, locale))
                .toList();
    }

    private McpClientGuide toGuide(McpClient client, Locale locale) {
        SpiGuideFile file = SpiGuideLoader.load(FAMILY, client.id(), locale);
        McpClientGuide g = new McpClientGuide();
        g.setId(client.id());
        g.setLabel(client.label());
        g.setColor(client.color());
        g.setDescription(file.description());
        g.setGuide(file.body());
        return g;
    }
}
