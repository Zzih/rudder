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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.request.LoginRequest;
import io.github.zzih.rudder.api.response.LoginResponse;
import io.github.zzih.rudder.api.response.PublicAuthSourceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireLoggedIn;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.service.auth.AuthService;
import io.github.zzih.rudder.service.auth.AuthSourceService;
import io.github.zzih.rudder.service.auth.security.DynamicLdapAuthenticationManager;
import io.github.zzih.rudder.service.coordination.ratelimit.RateLimitService;

import java.time.Duration;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录入口:
 * <ul>
 *   <li>{@code POST /login} —— 本地账号(走 AuthService.login,内部 BCrypt 校验)</li>
 *   <li>{@code POST /sources/{id}/login} —— 凭证类登录(LDAP)</li>
 * </ul>
 *
 * <p>OIDC 走 Spring Security {@code oauth2Login}:浏览器直访
 * {@code /oauth2/authorization/{sourceId}}(由
 * {@link io.github.zzih.rudder.api.security.DbClientRegistrationRepository} 反查 IdP),
 * 回调由 Spring 处理后 {@link io.github.zzih.rudder.api.security.JwtIssuanceSuccessHandler}
 * 签 Rudder JWT 并 302 前端。本 controller 不再有 sso/start 与 sso/callback。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int LOGIN_MAX_PERMITS = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);

    private final AuthService authService;
    private final AuthSourceService authSourceService;
    private final DynamicLdapAuthenticationManager ldapAuthenticationManager;
    private final RateLimitService rateLimitService;

    @GetMapping("/me")
    @RequireLoggedIn
    public Result<UserContext.UserInfo> me() {
        return Result.ok(UserContext.get());
    }

    /** 登录页拉的 source 列表 —— 仅启用 + 仅非敏感字段。 */
    @GetMapping("/sources")
    public Result<List<PublicAuthSourceResponse>> publicSources() {
        List<PublicAuthSourceResponse> list = authSourceService.listEnabledDetail().stream()
                .map(PublicAuthSourceResponse::from)
                .toList();
        return Result.ok(list);
    }

    @PostMapping("/login")
    @AuditLog(module = AuditModule.AUTH, action = AuditAction.LOGIN, resourceType = AuditResourceType.USER, description = "本地账号登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        enforceLoginRateLimit(http);
        AuthService.AuthResult result = authService.signByCredentials(request.getUsername(), request.getPassword());
        return Result.ok(toLoginResponse(result));
    }

    @PostMapping("/sources/{id}/login")
    @AuditLog(module = AuditModule.AUTH, action = AuditAction.LOGIN_LDAP, resourceType = AuditResourceType.USER, description = "通过 auth source 凭证登录")
    public Result<LoginResponse> loginBySource(@PathVariable Long id,
                                               @Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        enforceLoginRateLimit(http);
        AuthService.AuthResult result = ldapAuthenticationManager.authenticate(
                id, request.getUsername(), request.getPassword());
        return Result.ok(toLoginResponse(result));
    }

    /** 登录限流: 1 分钟同 IP 最多 10 次。本地 + LDAP 共用。 */
    private void enforceLoginRateLimit(HttpServletRequest http) {
        String clientIp = HttpUtils.resolveClientIp(http);
        if (!rateLimitService.tryAcquire("login", clientIp, LOGIN_MAX_PERMITS, LOGIN_WINDOW)) {
            log.warn("Login rate limit exceeded for IP {}", clientIp);
            throw new BizException(SystemErrorCode.TOO_MANY_REQUESTS,
                    "Too many login attempts, please try again later");
        }
    }

    private LoginResponse toLoginResponse(AuthService.AuthResult result) {
        // 登录时还没选 workspace,工作空间维度的角色无从得知;
        // SUPER_ADMIN 直接给,普通用户给 VIEWER 兜底,进 workspace 后 /auth/me 会覆盖。
        String role = Boolean.TRUE.equals(result.isSuperAdmin())
                ? RoleType.SUPER_ADMIN.name()
                : RoleType.VIEWER.name();
        return new LoginResponse(result.token(), result.userId(), result.username(),
                result.isSuperAdmin(), role);
    }
}
