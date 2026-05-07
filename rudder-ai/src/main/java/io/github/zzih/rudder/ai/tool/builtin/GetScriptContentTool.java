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
import io.github.zzih.rudder.service.script.ScriptService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** 按 code 获取脚本内容与元信息。 */
@Component
@RequiredArgsConstructor
public class GetScriptContentTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "scriptCode":{"type":"integer","description":"Script code (not id)"}
                      },
                      "required":["scriptCode"]
                    }""");

    private final ScriptService scriptService;

    @Override
    public String name() {
        return "get_script_content";
    }

    @Override
    public String description() {
        return "Get the full content and metadata of a script by its code. "
                + "Use this when the user wants to edit or reference an existing script.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long code = input.get("scriptCode").asLong();
        var script = scriptService.getByCode(ctx.getWorkspaceId(), code);
        ObjectNode node = JsonUtils.createObjectNode();
        node.put("id", script.getId());
        node.put("code", script.getCode());
        node.put("name", script.getName());
        if (script.getTaskType() != null) {
            node.put("taskType", script.getTaskType().name());
        }
        if (script.getDirId() != null) {
            node.put("dirId", script.getDirId());
        }
        node.put("content", script.getContent() == null ? "" : script.getContent());
        if (script.getParams() != null) {
            node.put("params", script.getParams());
        }
        return JsonUtils.toJson(node);
    }
}
