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

import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.AuthService;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Spring {@link OidcUserService} 之上钩 {@code findOrCreateSsoUser},把 OIDC sub 落到 t_r_user。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RudderOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final AuthService authService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String ssoId = oidcUser.getSubject();
        String username = pickUsername(oidcUser);
        String email = oidcUser.getEmail();
        String avatar = oidcUser.getPicture();
        authService.findOrCreateSsoUser(AuthSourceType.OIDC.name(), ssoId, username, email, avatar);
        return oidcUser;
    }

    /** 优先 preferred_username,其次 name,再回退 email 本地部分。 */
    private static String pickUsername(OidcUser u) {
        if (u.getPreferredUsername() != null && !u.getPreferredUsername().isBlank()) {
            return u.getPreferredUsername();
        }
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getFullName();
        }
        if (u.getEmail() != null) {
            return u.getEmail();
        }
        return null;
    }
}
