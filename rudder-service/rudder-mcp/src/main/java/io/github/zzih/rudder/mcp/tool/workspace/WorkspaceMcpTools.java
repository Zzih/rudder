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

package io.github.zzih.rudder.mcp.tool.workspace;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.WorkspaceService;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Workspace 域 MCP tools — 当前 token 绑定的 workspace 元信息（只读）。 */
@Service
@RequiredArgsConstructor
public class WorkspaceMcpTools {

    private final WorkspaceService workspaceService;
    private final MemberService memberService;

    @McpResource(uri = "rudder://workspace/current", name = "current-workspace", description = "Workspace this PAT is bound to (id / name / description). Implicit context for all other tools.", mimeType = "application/json")
    @McpCapability("workspace.view")
    public String current() {
        return JsonUtils.toJson(workspaceService.getById(UserContext.requireWorkspaceId()));
    }

    @McpResource(uri = "rudder://workspace/members", name = "workspace-members", description = "Members of the current workspace with their role (VIEWER / DEVELOPER / WORKSPACE_OWNER).", mimeType = "application/json")
    @McpCapability("workspace.view")
    public String listMembers() {
        return JsonUtils.toJson(memberService.listByWorkspaceId(UserContext.requireWorkspaceId()));
    }
}
