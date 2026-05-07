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

/** 删除脚本。写类工具,PermissionGate 会在只读模式下拒绝。 */
@Component
@RequiredArgsConstructor
public class DeleteScriptTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{"code":{"type":"integer"}},
                      "required":["code"]
                    }""");

    private final ScriptService scriptService;

    @Override
    public String name() {
        return "delete_script";
    }

    @Override
    public String description() {
        return "Delete a script by code. If the script is bound to a task, it is converted to TASK-source "
                + "instead of being physically removed (keeps downstream execution working).";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long code = input.get("code").asLong();
        scriptService.delete(ctx.getWorkspaceId(), code);

        ObjectNode out = JsonUtils.createObjectNode();
        out.put("code", code);
        out.put("deleted", true);
        return JsonUtils.toJson(out);
    }
}
