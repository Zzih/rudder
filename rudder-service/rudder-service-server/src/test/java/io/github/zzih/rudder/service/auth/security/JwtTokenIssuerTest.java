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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.dao.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenIssuerTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256-algo";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    private JwtTokenIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        JwtSecretEncoderConfig config = new JwtSecretEncoderConfig(SECRET);
        issuer = new JwtTokenIssuer(config.jwtEncoder(), ONE_HOUR_MILLIS);
        decoder = config.jwtDecoder();
    }

    @Test
    void issuedTokenContainsExpectedClaims() {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setIsSuperAdmin(true);

        String token = issuer.issue(user);
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(RudderJwtClaims.USERNAME)).isEqualTo("alice");
        assertThat(jwt.<Boolean>getClaim(RudderJwtClaims.SUPER_ADMIN)).isTrue();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void nonSuperAdminClaimIsFalse() {
        User user = new User();
        user.setId(7L);
        user.setUsername("bob");
        user.setIsSuperAdmin(false);

        Jwt jwt = decoder.decode(issuer.issue(user));
        assertThat(jwt.<Boolean>getClaim(RudderJwtClaims.SUPER_ADMIN)).isFalse();
    }

    @Test
    void tamperedTokenFailsDecoding() {
        User user = new User();
        user.setId(1L);
        user.setUsername("x");
        user.setIsSuperAdmin(false);
        String token = issuer.issue(user);
        // 翻转 payload 段任一字符即破坏签名
        String tampered = token.substring(0, token.length() - 5) + "AAAAA";
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }
}
