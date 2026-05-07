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
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.ScriptService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** 更新脚本正文。前端应在 tool_call 事件上呈现 diff → 用户 Apply → 后端真正落库。 */
@Component
@RequiredArgsConstructor
public class UpdateScriptTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "code":{"type":"integer","description":"Script code (from list_scripts)"},
                        "content":{"type":"string","description":"New full body. Pass the entire script, not just the diff."}
                      },
                      "required":["code","content"]
                    }""");

    private final ScriptService scriptService;

    @Override
    public String name() {
        return "update_script";
    }

    @Override
    public String description() {
        return "Rewrite the content of an existing script (full body). "
                + "Use when the user asked to modify or refactor a specific script. "
                + "Prefer to echo back a short summary of what changed. "
                + "Does not rename or move the script (use rename_script / move_script for those).";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long code = input.get("code").asLong();
        String content = input.get("content").asText();

        Script patch = new Script();
        patch.setContent(content);
        Script updated = scriptService.update(ctx.getWorkspaceId(), code, patch);

        ObjectNode out = JsonUtils.createObjectNode();
        out.put("code", updated.getCode());
        out.put("name", updated.getName());
        out.put("contentLength", content.length());
        return JsonUtils.toJson(out);
    }
}
