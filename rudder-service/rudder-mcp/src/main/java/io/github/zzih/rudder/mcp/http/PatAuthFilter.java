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

package io.github.zzih.rudder.mcp.http;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.PatCodec;
import io.github.zzih.rudder.mcp.auth.TokenView;
import io.github.zzih.rudder.service.auth.security.RudderAuthorities;

import java.io.IOException;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP PAT 认证过滤器 — 仅作用于 {@code /mcp} 路径(Spring AI MCP server 接管协议层)。
 *
 * <p>从 {@code Authorization: Bearer rdr_pat_xxx} header 解析 PAT，
 * 经 {@link McpTokenService#verify} 验证后注入 {@link UserContext}：
 * <ul>
 *   <li>userId = token.user_id</li>
 *   <li>workspaceId = token.workspace_id（强绑死，客户端无法覆盖）</li>
 *   <li>role = WorkspaceMember.role（运行时查 DB，反映最新角色）</li>
 * </ul>
 *
 * <p>验证失败 → 返回 401，链路终止。
 * 灰度：仅当 {@code spring.ai.mcp.server.enabled=true} 时启用此 filter。
 *
 * <p>@Order(0):早于 Servlet 容器内其他 Filter 执行。注意 Spring Security 主链对所有路径
 * permitAll,且 oauth2ResourceServer 仅在请求带 Bearer 头时才尝试解析;MCP 协议入口的鉴权完全
 * 在本 filter 内完成,与 Spring Security 不冲突。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class PatAuthFilter extends OncePerRequestFilter {

    /** Spring AI MCP server 协议端点前缀（默认 {@code /mcp}）。 */
    private static final String MCP_PATH_PREFIX = "/mcp";

    private final McpTokenService tokenService;
    private final UserDao userDao;
    private final WorkspaceMemberDao workspaceMemberDao;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(MCP_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing Bearer token");
            return;
        }
        String token = authHeader.substring(7).trim();
        if (!PatCodec.isWellFormed(token)) {
            sendUnauthorized(response, "Malformed MCP token");
            return;
        }

        Optional<TokenView> verified = tokenService.verify(token);
        if (verified.isEmpty()) {
            sendUnauthorized(response, "Invalid or expired MCP token");
            return;
        }

        TokenView view = verified.get();
        try {
            request.setAttribute(McpRequestAttributes.TOKEN_VIEW, view);

            UserContext.UserInfo userInfo = new UserContext.UserInfo();
            userInfo.setUserId(view.userId());
            userInfo.setWorkspaceId(view.workspaceId());

            User user = userDao.selectById(view.userId());
            if (user != null) {
                userInfo.setUsername(user.getUsername());
            }
            WorkspaceMember member = workspaceMemberDao.selectByWorkspaceIdAndUserId(
                    view.workspaceId(), view.userId());
            if (member != null) {
                userInfo.setRole(member.getRole());
            }
            UserContext.set(userInfo);
            applySecurityContext(userInfo);

            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /** MCP 路径不走 Spring Security 主链,显式塞 SecurityContext 让 service 层的 @PreAuthorize 仍生效。 */
    private static void applySecurityContext(UserContext.UserInfo userInfo) {
        Object principal = userInfo.getUsername() == null
                ? String.valueOf(userInfo.getUserId())
                : userInfo.getUsername();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, RudderAuthorities.from(userInfo.getRole())));
    }

    private static void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
