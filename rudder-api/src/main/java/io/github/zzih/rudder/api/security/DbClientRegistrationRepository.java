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

import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.auth.AuthSourceChangedEvent;
import io.github.zzih.rudder.service.auth.AuthSourceService;
import io.github.zzih.rudder.service.auth.security.OidcSourceConfigData;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 按 {@code sourceId} 反查 OIDC IdP 配置,通过 {@link ClientRegistrations#fromIssuerLocation}
 * 自动 discovery。Caffeine 1h TTL 兜底,变更由 {@link AuthSourceChangedEvent} 立即失效。
 */
@Slf4j
@Component
public class DbClientRegistrationRepository implements ClientRegistrationRepository {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final long CACHE_MAX_SIZE = 64;

    private final AuthSourceService authSourceService;
    private final Cache<Long, ClientRegistration> cache;

    public DbClientRegistrationRepository(AuthSourceService authSourceService) {
        this.authSourceService = authSourceService;
        this.cache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        long sourceId;
        try {
            sourceId = Long.parseLong(registrationId);
        } catch (NumberFormatException e) {
            return null;
        }
        try {
            return cache.get(sourceId, this::buildRegistration);
        } catch (BizException e) {
            log.warn("OIDC client registration unavailable for sourceId={}: {}", sourceId, e.getMessage());
            return null;
        }
    }

    public void evict(Long sourceId) {
        if (sourceId != null) {
            cache.invalidate(sourceId);
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    @EventListener
    public void onAuthSourceChanged(AuthSourceChangedEvent event) {
        evict(event.sourceId());
    }

    private ClientRegistration buildRegistration(Long sourceId) {
        OidcSourceConfigData cfg = authSourceService.getOidcConfig(sourceId);
        ClientRegistration.Builder builder = ClientRegistrations.fromIssuerLocation(cfg.getIssuer())
                .registrationId(String.valueOf(sourceId))
                .clientId(cfg.getClientId())
                .clientSecret(cfg.getClientSecret())
                .scope(parseScopes(cfg.getScopes()));
        String base = cfg.getCallbackBaseUrl();
        if (base != null && !base.isBlank()) {
            builder.redirectUri(base.replaceAll("/+$", "") + "/login/oauth2/code/{registrationId}");
        }
        return builder.build();
    }

    private static List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of("openid", "profile", "email");
        }
        // 兼容逗号 / 空格 / 分号分隔
        return Arrays.stream(scopes.split("[,\\s;]+"))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .toList();
    }
}
