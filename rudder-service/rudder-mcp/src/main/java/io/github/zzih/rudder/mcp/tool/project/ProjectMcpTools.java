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

package io.github.zzih.rudder.mcp.tool.project;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.WorkspaceGuard;
import io.github.zzih.rudder.service.workspace.ProjectService;
import io.github.zzih.rudder.service.workspace.dto.ProjectDTO;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Project 域 MCP tools 聚合（browse + author）。 */
@Service
@RequiredArgsConstructor
public class ProjectMcpTools {

    private final ProjectService projectService;
    private final WorkspaceGuard workspaceGuard;

    @McpTool(name = "project.list", description = "List projects in the current workspace.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("project.browse")
    public List<ProjectDTO> list() {
        return projectService.listByWorkspaceId(UserContext.requireWorkspaceId());
    }

    @McpResource(uri = "rudder://project/{code}", name = "rudder-project", description = "A project in the current workspace (groups workflows under the same business unit). Use project.list to discover codes.", mimeType = "application/json")
    @McpCapability("project.browse")
    public String get(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code required");
        }
        return JsonUtils.toJson(projectService.getByCode(UserContext.requireWorkspaceId(), Long.parseLong(code)));
    }

    /** URI variable {code} 自动补全 —— 返回当前 workspace 下匹配前缀的 project code 列表。 */
    @McpComplete(uri = "rudder://project/{code}")
    public List<String> completeProjectCode(String prefix) {
        return projectService.listByWorkspaceId(UserContext.requireWorkspaceId()).stream()
                .map(p -> p.getCode() == null ? null : p.getCode().toString())
                .filter(c -> c != null && (prefix == null || c.startsWith(prefix)))
                .limit(50)
                .toList();
    }

    @McpTool(name = "project.create", description = "Create a new project under current workspace.")
    @McpCapability("project.author")
    public ProjectDTO create(@McpToolParam(description = "Project body — name required", required = true) ProjectDTO body) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        return projectService.create(UserContext.requireWorkspaceId(), body);
    }

    @McpTool(name = "project.update", description = "Update project name/description/params by code.", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("project.author")
    public ProjectDTO update(
                             @McpToolParam(description = "project code", required = true) Long code,
                             @McpToolParam(description = "Patch fields — only non-null fields take effect", required = true) ProjectDTO body) {
        if (code == null) {
            throw new IllegalArgumentException("code required");
        }
        return projectService.update(UserContext.requireWorkspaceId(), code, body);
    }

    @McpTool(name = "project.delete", description = "Delete a project by code (rejects if it still contains workflows).", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("project.author")
    public void delete(
                       @McpToolParam(description = "project code", required = true) Long code) {
        if (code == null) {
            throw new IllegalArgumentException("code required");
        }
        workspaceGuard.requireProjectInWorkspace(code);
        projectService.delete(UserContext.requireWorkspaceId(), code);
    }
}
