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

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final UserDao userDao;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${rudder.security.jwt-secret}")
    private String secret;

    @Value("${rudder.security.jwt-expiration}")
    private long expiration;

    private SecretKey jwtKey;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    @PostConstruct
    void initKey() {
        this.jwtKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

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

    public AuthResult login(String username, String password) {
        log.info("用户登录, username={}", username);
        User user = userDao.selectByUsername(username);
        if (user == null) {
            log.warn("登录失败: 用户不存在, username={}", username);
            throw new NotFoundException(WorkspaceErrorCode.USER_NOT_FOUND);
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            log.warn("登录失败: 密码未设置, username={}", username);
            throw new AuthException(WorkspaceErrorCode.PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("登录失败: 密码错误, username={}", username);
            throw new AuthException(WorkspaceErrorCode.PASSWORD_ERROR);
        }
        log.info("用户登录成功, username={}, userId={}", username, user.getId());
        return new AuthResult(generateToken(user), user);
    }

    public AuthResult generateTokenForUser(User user) {
        return new AuthResult(generateToken(user), user);
    }

    /**
     * SSO/LDAP 共用：按 provider+ssoId 查找用户，或按 email 关联，或自动创建
     */
    public User findOrCreateSsoUser(String provider, String ssoId,
                                    String username, String email, String avatar) {
        log.info("SSO用户查找/创建, provider={}, ssoId={}, username={}", provider, ssoId, username);
        // 1. 按 SSO provider + ID 查找
        User user = userDao.selectBySso(provider, ssoId);
        if (user != null) {
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                userDao.updateById(user);
            }
            return user;
        }

        // 2. 按邮箱匹配已有用户（关联 SSO）
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

        // 3. 自动创建新用户
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

    public UserContext.UserInfo parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UserContext.UserInfo userInfo = new UserContext.UserInfo();
            userInfo.setUserId(claims.get("userId", Long.class));
            userInfo.setUsername(claims.get("username", String.class));
            Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);
            if (Boolean.TRUE.equals(isSuperAdmin)) {
                userInfo.setRole(RoleType.SUPER_ADMIN.name());
            }
            return userInfo;
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期");
            throw new AuthException(WorkspaceErrorCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            log.warn("Token解析失败: {}", e.getMessage());
            throw new AuthException(WorkspaceErrorCode.INVALID_TOKEN);
        }
    }

    private String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("isSuperAdmin", user.getIsSuperAdmin())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(jwtKey)
                .compact();
    }
}
