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
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.service.script.ScriptDirService;
import io.github.zzih.rudder.service.script.dto.ScriptDirDTO;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** ScriptDir 域 MCP tools — 脚本目录树管理（list / create / rename / move / delete）。 */
@Service
@RequiredArgsConstructor
public class ScriptDirMcpTools {

    private final ScriptDirService scriptDirService;

    @McpTool(name = "script_dir.list", description = "List all script directories in the current workspace (flat list with parent ids; reconstruct tree client-side if needed).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("script.browse")
    public List<ScriptDirDTO> list() {
        return scriptDirService.listByWorkspaceIdDetail(UserContext.requireWorkspaceId());
    }

    @McpTool(name = "script_dir.create", description = "Create a new script directory under given parent (body.parentId null = root).")
    @McpCapability("script.author")
    public ScriptDirDTO create(@McpToolParam(description = "Directory body — name required, parentId optional (null = root)", required = true) ScriptDirDTO body) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        return scriptDirService.createDetail(UserContext.requireWorkspaceId(), body);
    }

    @McpTool(name = "script_dir.rename", description = "Rename a script directory (body.name = new name).", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("script.author")
    public ScriptDirDTO rename(
                               @McpToolParam(description = "directory id", required = true) Long id,
                               @McpToolParam(description = "Body with new name", required = true) ScriptDirDTO body) {
        if (id == null || body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("id and name required");
        }
        return scriptDirService.updateDetail(UserContext.requireWorkspaceId(), id, body);
    }

    @McpTool(name = "script_dir.move", description = "Move a script directory under a new parent (body.parentId = target; null = root).", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("script.author")
    public ScriptDirDTO move(
                             @McpToolParam(description = "directory id", required = true) Long id,
                             @McpToolParam(description = "Body with target parentId (null = move to root)") ScriptDirDTO body) {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        return scriptDirService.moveDetail(UserContext.requireWorkspaceId(), id,
                body == null ? new ScriptDirDTO() : body);
    }

    @McpTool(name = "script_dir.delete", description = "Delete a script directory (must be empty).", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("script.author")
    public void delete(
                       @McpToolParam(description = "directory id", required = true) Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        scriptDirService.delete(UserContext.requireWorkspaceId(), id);
    }
}
