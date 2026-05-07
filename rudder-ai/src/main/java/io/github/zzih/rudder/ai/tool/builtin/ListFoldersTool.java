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

package io.github.zzih.rudder.ai.tool.builtin;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.ScriptDirService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** 列出当前工作区的所有脚本目录。 */
@Component
@RequiredArgsConstructor
public class ListFoldersTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            "{\"type\":\"object\",\"properties\":{}}");

    private final ScriptDirService scriptDirService;

    @Override
    public String name() {
        return "list_folders";
    }

    @Override
    public String description() {
        return "List all script folders in the current workspace. Returns id, name, parentId.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        var dirs = scriptDirService.listByWorkspaceId(ctx.getWorkspaceId());
        ArrayNode array = JsonUtils.createArrayNode();
        for (var dir : dirs) {
            ObjectNode node = array.addObject();
            node.put("id", dir.getId());
            node.put("name", dir.getName());
            if (dir.getParentId() != null) {
                node.put("parentId", dir.getParentId());
            }
        }
        return JsonUtils.toJson(array);
    }
}
