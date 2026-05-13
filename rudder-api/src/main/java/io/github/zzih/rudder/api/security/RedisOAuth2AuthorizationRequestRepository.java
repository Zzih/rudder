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

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 授权请求(state / nonce / PKCE)存 Redis 以保 stateless,5min TTL。
 * 序列化必须用 {@link SecurityJackson2Modules},否则 immutable + polymorphic attrs 反序列化失败。
 */
@Slf4j
@Component
public class RedisOAuth2AuthorizationRequestRepository
        implements
            AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String KEY_PREFIX = "oauth2:authz:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisOAuth2AuthorizationRequestRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = buildObjectMapper();
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader cl = RedisOAuth2AuthorizationRequestRepository.class.getClassLoader();
        mapper.registerModules(SecurityJackson2Modules.getModules(cl));
        return mapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + state);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize OAuth2AuthorizationRequest for state={}: {}", state, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String state = authorizationRequest.getState();
        try {
            String json = objectMapper.writeValueAsString(authorizationRequest);
            redisTemplate.opsForValue().set(KEY_PREFIX + state, json, TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        if (existing != null) {
            redisTemplate.delete(KEY_PREFIX + existing.getState());
        }
        return existing;
    }
}
