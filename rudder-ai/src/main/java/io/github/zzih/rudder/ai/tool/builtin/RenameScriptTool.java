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

/** 重命名脚本。仅修改 name,不动 content / dirId。 */
@Component
@RequiredArgsConstructor
public class RenameScriptTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "code":{"type":"integer"},
                        "newName":{"type":"string"}
                      },
                      "required":["code","newName"]
                    }""");

    private final ScriptService scriptService;

    @Override
    public String name() {
        return "rename_script";
    }

    @Override
    public String description() {
        return "Rename an existing script (does not change content or folder). Fails if a sibling with the same name exists.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long code = input.get("code").asLong();
        String newName = input.get("newName").asText();

        Script patch = new Script();
        patch.setName(newName);
        Script updated = scriptService.update(ctx.getWorkspaceId(), code, patch);

        ObjectNode out = JsonUtils.createObjectNode();
        out.put("code", updated.getCode());
        out.put("name", updated.getName());
        return JsonUtils.toJson(out);
    }
}
