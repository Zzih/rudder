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

package io.github.zzih.rudder.mcp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TokenViewCache} 是 {@link GlobalCacheService} 的薄包装,本测仅验证委托关系正确,
 * 不复测 GlobalCacheService 的失效广播 / TTL / Caffeine 行为(那些有自己的测试覆盖)。
 */
@ExtendWith(MockitoExtension.class)
class TokenViewCacheTest {

    @Mock
    private GlobalCacheService globalCacheService;

    @InjectMocks
    private TokenViewCache cache;

    private TokenView view(Long tokenId) {
        return new TokenView(tokenId, 100L, 42L, McpTokenStatus.ACTIVE,
                LocalDateTime.now().plusDays(1), Set.of("metadata.browse"));
    }

    @Test
    @DisplayName("getIfPresent 委托给 GlobalCacheService.get(MCP_TOKEN_VIEW, plainToken)")
    void getDelegates() {
        TokenView v = view(1L);
        when(globalCacheService.<TokenView>get(GlobalCacheKey.MCP_TOKEN_VIEW, "rdr_pat_aaa"))
                .thenReturn(v);

        assertThat(cache.getIfPresent("rdr_pat_aaa")).isSameAs(v);
        verify(globalCacheService).get(GlobalCacheKey.MCP_TOKEN_VIEW, "rdr_pat_aaa");
    }

    @Test
    @DisplayName("put 委托给 GlobalCacheService.put(MCP_TOKEN_VIEW, plainToken, view)")
    void putDelegates() {
        TokenView v = view(1L);
        cache.put("rdr_pat_aaa", v);
        verify(globalCacheService).put(GlobalCacheKey.MCP_TOKEN_VIEW, "rdr_pat_aaa", v);
    }

    @Test
    @DisplayName("invalidateAll 委托给 GlobalCacheService.invalidateAll(MCP_TOKEN_VIEW) 全集群清")
    void invalidateAllDelegates() {
        cache.invalidateAll();
        verify(globalCacheService).invalidateAll(eq(GlobalCacheKey.MCP_TOKEN_VIEW));
    }

    @Test
    @DisplayName("TokenView.hasCapability / isExpired (helper 验证)")
    void tokenViewHelpers() {
        TokenView v = view(1L);
        assertThat(v.hasCapability("metadata.browse")).isTrue();
        assertThat(v.hasCapability("script.author")).isFalse();
        assertThat(v.isExpired()).isFalse();

        TokenView expired = new TokenView(1L, 100L, 42L, McpTokenStatus.ACTIVE,
                LocalDateTime.now().minusDays(1), Set.of());
        assertThat(expired.isExpired()).isTrue();
    }
}
