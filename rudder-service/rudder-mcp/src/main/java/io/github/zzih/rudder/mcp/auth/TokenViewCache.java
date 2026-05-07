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

import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 已验证 PAT 的 5 秒结果缓存,作用 = 跳过 bcrypt 慢哈希在热路径上重复跑。
 *
 * <p>本类是 {@link GlobalCacheService} {@link GlobalCacheKey#MCP_TOKEN_VIEW} 的薄包装,
 * 无自管 Caffeine / 无自管广播 —— 跨节点失效复用 GlobalCacheService 的统一通道。
 *
 * <p>失效语义:撤销 / 角色降级 / 审批激活 → 整组清(所有节点 MCP token cache 清空 → 下次各自
 * 重 bcrypt 一次,80ms 单次无感)。"按 tokenId 精确失效"被故意放弃 —— 收益小、增加复杂度。
 */
@Component
@RequiredArgsConstructor
public class TokenViewCache {

    private final GlobalCacheService cache;

    public TokenView getIfPresent(String plainToken) {
        return cache.get(GlobalCacheKey.MCP_TOKEN_VIEW, plainToken);
    }

    public void put(String plainToken, TokenView view) {
        cache.put(GlobalCacheKey.MCP_TOKEN_VIEW, plainToken, view);
    }

    /**
     * 单条失效。cache hit 时发现 entry 已过期(在 5s TTL 边界附近),清掉避免再次 hit。
     * 委托给 GlobalCacheService,会跨节点广播(其他节点没此 entry 时为 no-op)。
     */
    public void invalidate(String plainToken) {
        cache.invalidate(GlobalCacheKey.MCP_TOKEN_VIEW, plainToken);
    }

    /**
     * 撤销 / 角色降级 / 审批激活 时调用 —— 全集群清空 MCP token cache。
     * 不区分 tokenId,代价是其他用户下次调用重 bcrypt 一次(80ms),撤销发生频率低,可忽略。
     */
    public void invalidateAll() {
        cache.invalidateAll(GlobalCacheKey.MCP_TOKEN_VIEW);
    }
}
