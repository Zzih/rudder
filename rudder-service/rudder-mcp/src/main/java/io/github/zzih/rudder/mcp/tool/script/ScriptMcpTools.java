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

package io.github.zzih.rudder.mcp.tool.script;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.service.script.dto.ScriptDTO;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Script 域 MCP tools 聚合（browse + author）。 */
@Service
@RequiredArgsConstructor
public class ScriptMcpTools {

    private final ScriptService scriptService;

    @McpTool(name = "script.list", description = "List all scripts in the current workspace.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("script.browse")
    public List<ScriptDTO> list() {
        return scriptService.listByWorkspaceIdDetail(UserContext.requireWorkspaceId());
    }

    @McpResource(uri = "rudder://script/{code}", name = "rudder-script", description = "A script in the current workspace (SQL/Python/Shell/etc., addressed by stable code). Use script.list to discover available codes.", mimeType = "application/json")
    @McpCapability("script.browse")
    public String get(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code required");
        }
        return JsonUtils.toJson(scriptService.getByCodeDetail(UserContext.requireWorkspaceId(), Long.parseLong(code)));
    }

    /** URI variable {code} 自动补全 —— 返回当前 workspace 下匹配前缀的 script code 列表。 */
    @McpComplete(uri = "rudder://script/{code}")
    public List<String> completeScriptCode(String prefix) {
        return scriptService.listByWorkspaceIdDetail(UserContext.requireWorkspaceId()).stream()
                .map(s -> s.getCode() == null ? null : s.getCode().toString())
                .filter(c -> c != null && (prefix == null || c.startsWith(prefix)))
                .limit(50)
                .toList();
    }

    @McpTool(name = "script.create", description = "Create a new script under given directory.")
    @McpCapability("script.author")
    public ScriptDTO create(@McpToolParam(description = "Script body — name + taskType required; dirId null = workspace root", required = true) ScriptDTO body) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (body.getTaskType() == null) {
            throw new IllegalArgumentException("taskType required");
        }
        return scriptService.createDetail(UserContext.requireWorkspaceId(), body);
    }

    @McpTool(name = "script.update", description = "Update an existing script (only non-null fields are applied).", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("script.author")
    public ScriptDTO update(
                            @McpToolParam(description = "script code", required = true) Long code,
                            @McpToolParam(description = "Patch fields — only non-null name/taskType/content take effect", required = true) ScriptDTO body) {
        if (code == null) {
            throw new IllegalArgumentException("code required");
        }
        return scriptService.updateDetail(UserContext.requireWorkspaceId(), code, body);
    }

    @McpTool(name = "script.delete", description = "Delete a script by code (or detach if bound to a task).", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("script.author")
    public void delete(
                       @McpToolParam(description = "script code", required = true) Long code) {
        if (code == null) {
            throw new IllegalArgumentException("code required");
        }
        scriptService.delete(UserContext.requireWorkspaceId(), code);
    }
}
