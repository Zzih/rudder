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

import io.github.zzih.rudder.service.auth.AuthSourceChangedEvent;
import io.github.zzih.rudder.service.auth.AuthSourceService;

import java.time.Duration;
import java.util.Hashtable;

import org.springframework.context.event.EventListener;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 按 sourceId 构造并缓存 {@link LdapAuthenticationProvider}(每个 source 一个,
 * 内部持独立 JNDI 连接池)。1 小时 TTL 兜底,真正失效靠 {@link AuthSourceService}
 * 在 create/update/delete 时主动调 {@link #evict(Long)}。
 *
 * <p>{@code trustAllCerts=true} 时通过 {@code java.naming.ldap.factory.socket} 注入
 * {@link TrustAllSslSocketFactory} 跳过证书校验(仅开发/测试)。
 *
 * <p>Spring LDAP 的 {@link LdapContextSource} 没有显式 dispose API,evict 后由 GC 回收。
 */
@Slf4j
@Component
public class DbLdapAuthenticationProviderFactory {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final long CACHE_MAX_SIZE = 32;

    private final AuthSourceService authSourceService;
    private final Cache<Long, LdapAuthenticationProvider> providerCache;

    public DbLdapAuthenticationProviderFactory(AuthSourceService authSourceService) {
        this.authSourceService = authSourceService;
        this.providerCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    public LdapAuthenticationProvider getProvider(Long sourceId) {
        return providerCache.get(sourceId, this::build);
    }

    public void evict(Long sourceId) {
        if (sourceId != null) {
            providerCache.invalidate(sourceId);
        }
    }

    public void evictAll() {
        providerCache.invalidateAll();
    }

    @EventListener
    public void onAuthSourceChanged(AuthSourceChangedEvent event) {
        evict(event.sourceId());
    }

    private LdapAuthenticationProvider build(Long sourceId) {
        LdapSourceConfigData cfg = authSourceService.getLdapConfig(sourceId);
        LdapContextSource contextSource = buildContextSource(cfg);
        FilterBasedLdapUserSearch search =
                new FilterBasedLdapUserSearch("", cfg.getUserSearchFilter(), contextSource);
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(search);
        return new LdapAuthenticationProvider(authenticator);
    }

    private LdapContextSource buildContextSource(LdapSourceConfigData cfg) {
        DefaultSpringSecurityContextSource src = new DefaultSpringSecurityContextSource(cfg.getUrl());
        src.setBase(cfg.getBaseDn());
        if (cfg.getBindDn() != null && !cfg.getBindDn().isBlank()) {
            src.setUserDn(cfg.getBindDn());
            src.setPassword(cfg.getBindPassword() == null ? "" : cfg.getBindPassword());
        }
        if (cfg.isTrustAllCerts()) {
            log.warn("LDAP TLS certificate validation disabled for url={}", cfg.getUrl());
            Hashtable<String, Object> env = new Hashtable<>();
            env.put("java.naming.ldap.factory.socket", TrustAllSslSocketFactory.class.getName());
            src.setBaseEnvironmentProperties(env);
        }
        src.afterPropertiesSet();
        return src;
    }
}
