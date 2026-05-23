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

package io.github.zzih.rudder.mcp.auth;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.auth.security.RudderAuthorities;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.UserService;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析 {@code Authorization: Bearer rdr_pat_xxx},经 {@link McpTokenService#verify} 验证后注入
 * {@link UserContext}:userId / workspaceId 取自 token(强绑死);role 运行时查 member 表(反映最新角色)。
 * 验证失败返 401。
 */
@Slf4j
@RequiredArgsConstructor
public class PatAuthFilter extends OncePerRequestFilter {

    private final McpTokenService tokenService;
    private final UserService userService;
    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
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

            // user / member 仅用于审计 username + role 注入;PAT 已 verify,这两步缺失不阻断鉴权。
            // UserService.getById 在用户被删时抛 NotFoundException — PAT 长生命周期里 user 可能被
            // DBA 误删 / 软删,降级到 username=null + 默认角色,而不是整条 MCP 通道 500。
            try {
                User user = userService.getById(view.userId());
                userInfo.setUsername(user.getUsername());
            } catch (NotFoundException e) {
                log.warn("MCP token verified but user_id={} not found, proceeding with anonymous username",
                        view.userId());
            }
            WorkspaceMember member = memberService.getMember(view.workspaceId(), view.userId());
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

    /** 显式注入 SecurityContext,让 service 层 @PreAuthorize 在 mcpFilterChain 路径下仍生效。 */
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
