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

package io.github.zzih.rudder.api.filter;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.auth.AuthService;
import io.github.zzih.rudder.service.workspace.MemberService;

import java.io.IOException;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TokenFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/sources", // 公开列表 + 按 source 派发的登录入口(LDAP / OIDC start / callback)
            "/api/approvals/callback",
            "/mcp", // MCP 协议入口 (Spring AI 接管) 走 PatAuthFilter (PAT), 跳过 JWT
            "/swagger-ui",
            "/v3/api-docs");

    private final AuthService authService;
    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);
            if (token != null) {
                try {
                    UserContext.UserInfo userInfo = authService.parseToken(token);
                    // 读取 X-Workspace-Id，查询该用户在工作空间中的角色
                    enrichWorkspaceRole(request, userInfo);
                    UserContext.set(userInfo);
                } catch (Exception e) {
                    // Bearer 存在但解析失败（过期/篡改）→ 立即 401，避免下游拦截器漏检
                    log.warn("Invalid token for path {}: {}", path, e.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"Invalid or expired token\"}");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 从请求头 X-Workspace-Id 获取工作空间ID，查询用户的工作空间角色并注入 UserContext。
     * SUPER_ADMIN 用户已在 parseToken 中设置角色，此处补充 workspaceId 即可。
     */
    private void enrichWorkspaceRole(HttpServletRequest request, UserContext.UserInfo userInfo) {
        String wsHeader = request.getHeader("X-Workspace-Id");
        if (wsHeader == null || wsHeader.isBlank()) {
            return;
        }
        try {
            Long workspaceId = Long.parseLong(wsHeader.trim());
            userInfo.setWorkspaceId(workspaceId);

            // SUPER_ADMIN 已有最高权限，无需查询成员表
            if (RoleType.SUPER_ADMIN.name().equals(userInfo.getRole())) {
                return;
            }

            // 查询该用户在该工作空间的成员角色
            WorkspaceMember member = memberService.getMember(workspaceId, userInfo.getUserId());
            if (member != null) {
                userInfo.setRole(member.getRole());
            }
            // member == null 时 role 保持 null，后续 PermissionInterceptor 会根据 @RequireRole 拒绝
        } catch (NumberFormatException e) {
            log.warn("Invalid X-Workspace-Id header: {}", wsHeader);
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 从 Authorization 头或 URL query 提取 JWT。
     * URL query 用于浏览器原生下载等无法设置请求头的场景。
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }
        return null;
    }
}
