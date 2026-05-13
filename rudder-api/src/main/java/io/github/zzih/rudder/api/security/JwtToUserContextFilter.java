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

package io.github.zzih.rudder.api.security;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.auth.security.RudderAuthorities;
import io.github.zzih.rudder.service.auth.security.RudderJwtClaims;
import io.github.zzih.rudder.service.workspace.MemberService;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 桥接:Spring SecurityContext.Jwt + X-Workspace-Id → Rudder {@link UserContext} ThreadLocal。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtToUserContextFilter extends OncePerRequestFilter {

    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                UserContext.UserInfo userInfo = toUserInfo(jwtAuth.getToken());
                enrichWorkspaceRole(request, userInfo);
                UserContext.set(userInfo);
                // 默认 JwtAuthenticationToken authorities 来自 scope claim,不含 workspace role
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                        jwtAuth.getToken(),
                        RudderAuthorities.from(userInfo.getRole()),
                        userInfo.getUsername()));
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private UserContext.UserInfo toUserInfo(Jwt jwt) {
        UserContext.UserInfo userInfo = new UserContext.UserInfo();
        userInfo.setUserId(Long.valueOf(jwt.getSubject()));
        userInfo.setUsername(jwt.getClaimAsString(RudderJwtClaims.USERNAME));
        Boolean superAdmin = jwt.getClaim(RudderJwtClaims.SUPER_ADMIN);
        if (Boolean.TRUE.equals(superAdmin)) {
            userInfo.setRole(RoleType.SUPER_ADMIN.name());
        }
        return userInfo;
    }

    /** SUPER_ADMIN 跳过成员表查询;普通用户按 workspace role 注入。 */
    private void enrichWorkspaceRole(HttpServletRequest request, UserContext.UserInfo userInfo) {
        String wsHeader = request.getHeader("X-Workspace-Id");
        if (wsHeader == null || wsHeader.isBlank()) {
            return;
        }
        try {
            Long workspaceId = Long.parseLong(wsHeader.trim());
            userInfo.setWorkspaceId(workspaceId);
            if (RoleType.SUPER_ADMIN.name().equals(userInfo.getRole())) {
                return;
            }
            WorkspaceMember member = memberService.getMember(workspaceId, userInfo.getUserId());
            if (member != null) {
                userInfo.setRole(member.getRole());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid X-Workspace-Id header: {}", wsHeader);
        }
    }
}
