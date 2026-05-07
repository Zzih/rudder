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

import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Token 视图 — bcrypt 验证通过后的不可变快照，缓存在 Caffeine 里供热路径复用。
 *
 * <p>5s TTL，避免每次 tool 调用都跑 bcrypt（约 80-100ms / 次）。
 * 撤销 / 角色降级时通过 {@code McpTokenInvalidator} 主动 evict + 集群广播。
 *
 * @param tokenId       数据库 token id
 * @param userId        归属用户
 * @param workspaceId   绑定的唯一 workspace
 * @param status        token 状态（命中缓存时一律 ACTIVE，过期/撤销已被 evict）
 * @param expiresAt     过期时间（运行时再校验一次，防止 cache TTL 内 token 已过期）
 * @param activeCapabilities ACTIVE 状态的 capability id 集合（scope 闸门读这里）
 */
public record TokenView(
        Long tokenId,
        Long userId,
        Long workspaceId,
        McpTokenStatus status,
        LocalDateTime expiresAt,
        Set<String> activeCapabilities) {

    public TokenView {
        activeCapabilities = activeCapabilities == null ? Set.of() : Set.copyOf(activeCapabilities);
    }

    public boolean hasCapability(String capabilityId) {
        return activeCapabilities.contains(capabilityId);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}
