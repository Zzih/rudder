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

package io.github.zzih.rudder.service.auth;

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.config.OidcSourceConfig;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenID Connect 授权码流程。state 由 controller 通过 {@code OneShotTokenService} 生成 / 校验,
 * 本 authenticator 只负责拼跳转 URL 和处理 callback 的 code → token → userinfo → user 流程。
 */
@Slf4j
@Component
public class OidcAuthenticator extends AbstractAuthenticator implements SsoAuthenticator {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public OidcAuthenticator(AuthService authService) {
        super(authService);
    }

    @Override
    public AuthSourceType type() {
        return AuthSourceType.OIDC;
    }

    @Override
    public String buildSignInUrl(AuthSource source, String state) {
        OidcSourceConfig config = parseConfig(source, OidcSourceConfig.class);
        return config.getAuthorizationUri()
                + "?client_id=" + encode(config.getClientId())
                + "&redirect_uri=" + encode(config.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode(config.getScopes())
                + "&state=" + encode(state);
    }

    @Override
    public AuthService.AuthResult handleCallback(AuthSource source, String code, String state) {
        OidcSourceConfig config = parseConfig(source, OidcSourceConfig.class);

        // 用 code 换 access_token
        String tokenBody = "client_id=" + encode(config.getClientId())
                + "&client_secret=" + encode(config.getClientSecret())
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(config.getRedirectUri())
                + "&grant_type=authorization_code";

        JsonNode tokenResp = postForm(config.getTokenUri(), tokenBody);
        String accessToken = tokenResp.path("access_token").asText();
        if (accessToken == null || accessToken.isBlank()) {
            // 不要打整个 tokenResp(可能含 refresh_token / id_token),只取 OIDC 错误字段
            log.error("OIDC token exchange failed: error={}, error_description={}",
                    tokenResp.path("error").asText("unknown"),
                    tokenResp.path("error_description").asText(""));
            throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }

        JsonNode userResp = getJson(config.getUserInfoUri(), accessToken);
        String ssoId = userResp.path("sub").asText();
        String name = userResp.has("preferred_username")
                ? userResp.path("preferred_username").asText()
                : userResp.path("name").asText(null);
        String email = userResp.path("email").asText(null);
        String avatar = userResp.path("picture").asText(null);

        String username = (name != null && !name.isBlank()) ? name
                : (email != null ? email.split("@")[0] : null);

        User user = authService.findOrCreateSsoUser(type().name(), ssoId, username, email, avatar);
        return authService.generateTokenForUser(user);
    }

    @Override
    public URI buildSuccessRedirect(AuthSource source, String token) {
        OidcSourceConfig config = parseConfig(source, OidcSourceConfig.class);
        return URI.create(config.getFrontendRedirectUrl() + "?token=" + encode(token));
    }

    @Override
    public URI buildFailureRedirect(AuthSource source, String errorCode) {
        // config 可能解析失败(例如 configJson 损坏),fallback 到根路径,避免 callback 抛二次异常
        String base;
        try {
            base = parseConfig(source, OidcSourceConfig.class).getFrontendRedirectUrl();
        } catch (BizException e) {
            base = "/";
        }
        return URI.create(base + "?error=" + encode(errorCode));
    }

    @Override
    public HealthStatus testConnection(AuthSource source) {
        OidcSourceConfig config;
        try {
            config = parseConfig(source, OidcSourceConfig.class);
        } catch (BizException e) {
            return HealthStatus.unhealthy("config invalid");
        }
        // 探活: GET issuer/.well-known/openid-configuration,只看 200。
        // 即使 IdP 没暴露这个 endpoint(自建 IdP 偶尔不规范),也至少看 authorization_uri 可达。
        String probeUrl = config.getIssuer() != null && !config.getIssuer().isBlank()
                ? trimTrailingSlash(config.getIssuer()) + "/.well-known/openid-configuration"
                : config.getAuthorizationUri();
        if (probeUrl == null || probeUrl.isBlank()) {
            return HealthStatus.unhealthy("missing issuer / authorization_uri");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 400) {
                return HealthStatus.healthy();
            }
            return HealthStatus.degraded("probe HTTP " + status);
        } catch (Exception e) {
            return HealthStatus.unhealthy(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JsonNode postForm(String url, String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return JsonUtils.getObjectMapper().readTree(response.body());
        } catch (Exception e) {
            throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }
    }

    private JsonNode getJson(String url, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return JsonUtils.getObjectMapper().readTree(response.body());
        } catch (Exception e) {
            throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
