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

package io.github.zzih.rudder.service.auth.security;

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.AuthService;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 按 sourceId 派发 LDAP 认证。userDn 作为 SSO ssoId 落 t_r_user。
 * 失败统一抛 {@link WorkspaceErrorCode#INVALID_CREDENTIALS} 防 user enumeration。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicLdapAuthenticationManager {

    private final DbLdapAuthenticationProviderFactory providerFactory;
    private final AuthService authService;

    public AuthService.AuthResult authenticate(Long sourceId, String username, String password) {
        LdapAuthenticationProvider provider;
        try {
            provider = providerFactory.getProvider(sourceId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("LDAP provider build failed for sourceId={}", sourceId, e);
            throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }

        Authentication result;
        try {
            result = provider.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            log.warn("LDAP authn failed for username={} sourceId={}: {}", username, sourceId, e.getMessage());
            throw new AuthException(WorkspaceErrorCode.INVALID_CREDENTIALS);
        }

        LdapUserDetails details = (LdapUserDetails) result.getPrincipal();
        String userDn = details.getDn();
        User user = authService.findOrCreateSsoUser(AuthSourceType.LDAP.name(), userDn, username, null, null);
        return authService.generateTokenForUser(user);
    }
}
