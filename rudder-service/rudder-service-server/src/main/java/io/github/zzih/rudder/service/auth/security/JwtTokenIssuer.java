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

import io.github.zzih.rudder.dao.entity.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 签发 Rudder 会话 JWT(claim: {@code sub} = userId / {@link RudderJwtClaims#USERNAME} /
 * {@link RudderJwtClaims#SUPER_ADMIN})。workspace 级 role 由 filter 运行时算,不进 token。
 */
@Component
public class JwtTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final long expirationMillis;

    public JwtTokenIssuer(JwtEncoder jwtEncoder,
                          @Value("${rudder.security.jwt-expiration}") long expirationMillis) {
        this.jwtEncoder = jwtEncoder;
        this.expirationMillis = expirationMillis;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim(RudderJwtClaims.USERNAME, user.getUsername())
                .claim(RudderJwtClaims.SUPER_ADMIN, Boolean.TRUE.equals(user.getIsSuperAdmin()))
                .issuedAt(now)
                .expiresAt(now.plus(expirationMillis, ChronoUnit.MILLIS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
