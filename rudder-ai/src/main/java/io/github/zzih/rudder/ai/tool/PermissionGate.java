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

package io.github.zzih.rudder.ai.tool;

import io.github.zzih.rudder.ai.permission.ToolConfigService;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.entity.AiToolConfig;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.mcp.McpToolNames;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行前的权限门闸。
 * <p>
 * 查 {@code t_r_ai_tool_config} 命中覆盖内置默认;未命中走 hardcoded 规则。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionGate {

    private final ToolConfigService toolConfigService;

    /** 最小角色要求:DB 覆盖 > 内置规则。 */
    public RoleType requiredRoleFor(String toolName, Long workspaceId) {
        AiToolConfig override = lookup(toolName, workspaceId);
        if (override != null && override.getMinRole() != null) {
            try {
                return RoleType.valueOf(override.getMinRole());
            } catch (IllegalArgumentException ignored) {
                // fall through to defaults
            }
        }
        return defaultRole(toolName);
    }

    public RoleType requiredRoleFor(String toolName) {
        return requiredRoleFor(toolName, null);
    }

    private RoleType defaultRole(String toolName) {
        if (toolName == null) {
            return RoleType.SUPER_ADMIN;
        }
        String localName = stripMcpPrefix(toolName);
        if (localName.startsWith("list_") || localName.startsWith("get_")
                || localName.startsWith("describe_") || localName.startsWith("read_")
                || localName.startsWith("search_")
                || localName.startsWith("run_sql_readonly") || localName.startsWith("sample_")) {
            return RoleType.VIEWER;
        }
        if (localName.startsWith("create_") || localName.startsWith("update_")
                || localName.startsWith("delete_") || localName.startsWith("rename_")
                || localName.startsWith("move_") || localName.startsWith("write_")
                || localName.startsWith("execute_")) {
            return RoleType.DEVELOPER;
        }
        return RoleType.DEVELOPER;
    }

    /** 写类工具需要前端弹确认。DB 覆盖 > 内置规则。 */
    public boolean requiresConfirm(String toolName) {
        return requiresConfirm(toolName, null);
    }

    public boolean requiresConfirm(String toolName, Long workspaceId) {
        AiToolConfig override = lookup(toolName, workspaceId);
        if (override != null && override.getRequireConfirm() != null) {
            return override.getRequireConfirm();
        }
        return defaultRequiresConfirm(toolName);
    }

    private boolean defaultRequiresConfirm(String toolName) {
        if (toolName == null) {
            return true;
        }
        String localName = stripMcpPrefix(toolName);
        if (localName.startsWith("delete_") || localName.equals("execute_sql_mutation")
                || localName.startsWith("write_") || localName.startsWith("execute_")) {
            return true;
        }
        return localName.startsWith("update_") || localName.startsWith("rename_") || localName.startsWith("move_");
    }

    /** 只读模式允许:DB 覆盖 > 内置规则。 */
    public boolean allowedInReadOnly(String toolName) {
        return allowedInReadOnly(toolName, null);
    }

    public boolean allowedInReadOnly(String toolName, Long workspaceId) {
        AiToolConfig override = lookup(toolName, workspaceId);
        if (override != null && override.getReadOnly() != null) {
            return override.getReadOnly();
        }
        return defaultAllowedInReadOnly(toolName);
    }

    private boolean defaultAllowedInReadOnly(String toolName) {
        if (toolName == null) {
            return false;
        }
        String localName = stripMcpPrefix(toolName);
        return localName.startsWith("list_") || localName.startsWith("get_")
                || localName.startsWith("describe_") || localName.startsWith("read_")
                || localName.startsWith("search_")
                || localName.startsWith("run_sql_readonly") || localName.startsWith("sample_");
    }

    /** 把 {@code mcp:{server}.{tool}} 里的服务器前缀剥掉,剩下的 {@code tool} 走通用前缀规则。 */
    private static String stripMcpPrefix(String toolName) {
        return McpToolNames.toolOf(toolName);
    }

    /** 执行前校验。不通过抛 {@link ToolPermissionDeniedException}。 */
    public void check(String toolName, ToolExecutionContext ctx) {
        if (ctx.isReadOnly() && !allowedInReadOnly(toolName, ctx.getWorkspaceId())) {
            throw new ToolPermissionDeniedException(
                    "tool " + toolName + " is not allowed in read-only mode");
        }
        RoleType required = requiredRoleFor(toolName, ctx.getWorkspaceId());
        RoleType actual = resolveUserRole(ctx.getUserRole());
        if (actual.getLevel() < required.getLevel()) {
            throw new ToolPermissionDeniedException(
                    "tool " + toolName + " requires role " + required.name()
                            + " or above, current: " + actual.name());
        }
    }

    /** role 字符串 → 枚举。null / 非法 → VIEWER(最严)。 */
    private static RoleType resolveUserRole(String role) {
        if (role == null || role.isBlank()) {
            return RoleType.VIEWER;
        }
        try {
            return RoleType.of(role);
        } catch (IllegalArgumentException e) {
            return RoleType.VIEWER;
        }
    }

    /** null 表示"该 workspace 不受 config 影响,走代码默认"。对 enabled=false 的 config 也返回 null(ToolRegistry 另行处理可见性过滤)。 */
    private AiToolConfig lookup(String toolName, Long workspaceId) {
        if (toolName == null) {
            return null;
        }
        try {
            AiToolConfig cfg = toolConfigService.find(toolName, workspaceId);
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
                // enabled=false 意味着"在这个 workspace 隐藏此 tool",权限规则层面视为无覆盖
                // (实际 runtime 根本调不到,ToolRegistry 会把它过滤掉)
                return null;
            }
            return cfg;
        } catch (Exception e) {
            log.debug("tool config lookup failed for {}: {}", toolName, e.getMessage());
            return null;
        }
    }

    public static final class ToolPermissionDeniedException extends RuntimeException {

        public ToolPermissionDeniedException(String message) {
            super(message);
        }
    }
}
