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
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.service.auth.security.JwtTokenIssuer;
import io.github.zzih.rudder.service.auth.security.RudderUserPrincipal;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserDao userDao;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final AuthenticationManager authenticationManager;

    public record AuthResult(String token, User user) {

        /** 给 controller 用的 primitive accessor,避免外部 import User entity。 */
        public Long userId() {
            return user != null ? user.getId() : null;
        }

        public String username() {
            return user != null ? user.getUsername() : null;
        }

        public Boolean isSuperAdmin() {
            return user != null ? user.getIsSuperAdmin() : null;
        }
    }

    /**
     * 本地账号登录:Spring Security {@link AuthenticationManager} 委派给
     * {@code DaoAuthenticationProvider} + {@code RudderUserDetailsService}(BCrypt 比对),
     * 失败统一抛 {@link WorkspaceErrorCode#INVALID_CREDENTIALS} 防 user enumeration。
     */
    public AuthResult signByCredentials(String username, String password) {
        log.info("用户登录, username={}", username);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            log.warn("登录失败: username={}, reason={}", username, e.getMessage());
            throw new AuthException(WorkspaceErrorCode.INVALID_CREDENTIALS);
        }
        RudderUserPrincipal principal = (RudderUserPrincipal) auth.getPrincipal();
        User user = new User();
        user.setId(principal.getUserId());
        user.setUsername(principal.getUsername());
        user.setIsSuperAdmin(principal.isSuperAdmin());
        log.info("用户登录成功, username={}, userId={}", username, principal.getUserId());
        return new AuthResult(jwtTokenIssuer.issue(user), user);
    }

    public AuthResult generateTokenForUser(User user) {
        return new AuthResult(jwtTokenIssuer.issue(user), user);
    }

    /** OIDC / LDAP 共用三段匹配:(provider, ssoId) → email → 自动建。 */
    public User findOrCreateSsoUser(String provider, String ssoId,
                                    String username, String email, String avatar) {
        log.info("SSO 用户查找/创建, provider={}, ssoId={}, username={}", provider, ssoId, username);
        User user = userDao.selectBySso(provider, ssoId);
        if (user != null) {
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                userDao.updateById(user);
            }
            return user;
        }
        if (email != null && !email.isBlank()) {
            user = userDao.selectByEmail(email);
            if (user != null) {
                user.setSsoProvider(provider);
                user.setSsoId(ssoId);
                if (avatar != null) {
                    user.setAvatar(avatar);
                }
                userDao.updateById(user);
                return user;
            }
        }
        String safeUsername = ensureUniqueUsername(username);
        user = new User();
        user.setUsername(safeUsername);
        user.setPassword("");
        user.setEmail(email);
        user.setAvatar(avatar);
        user.setSsoProvider(provider);
        user.setSsoId(ssoId);
        userDao.insert(user);
        return user;
    }

    private String ensureUniqueUsername(String username) {
        if (username == null || username.isBlank()) {
            return "user_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (userDao.countByUsername(username) == 0) {
            return username;
        }
        return username + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

}
