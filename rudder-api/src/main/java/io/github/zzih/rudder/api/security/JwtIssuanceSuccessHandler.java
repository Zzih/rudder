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

import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.security.JwtTokenIssuer;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** OIDC 登录成功 → 签 Rudder JWT → 302 到 SPA 落地页 {@value SPA_CALLBACK_PATH}(token 作为 query)。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtIssuanceSuccessHandler implements AuthenticationSuccessHandler {

    /** 与 vite.config base 对齐;改这里需同步改 vite。 */
    private static final String SPA_CALLBACK_PATH = "/ui/sso/callback";

    private final JwtTokenIssuer jwtTokenIssuer;
    private final UserDao userDao;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2)) {
            log.error("Unexpected Authentication type: {}", authentication.getClass());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        if (!(oauth2.getPrincipal() instanceof OidcUser oidcUser)) {
            log.error("Unexpected principal type: {}", oauth2.getPrincipal().getClass());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        Long sourceId = Long.valueOf(oauth2.getAuthorizedClientRegistrationId());
        User user = userDao.selectBySso(AuthSourceType.OIDC.name(), oidcUser.getSubject());
        if (user == null) {
            log.error("OIDC user missing after auth: sub={}, sourceId={}", oidcUser.getSubject(), sourceId);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        String token = jwtTokenIssuer.issue(user);
        String redirectUrl = UriComponentsBuilder.fromUriString(SPA_CALLBACK_PATH)
                .queryParam("token", token)
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
