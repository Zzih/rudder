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

package io.github.zzih.rudder.mcp.event;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;
import io.github.zzih.rudder.dao.dao.McpTokenDao;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpToken;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.TokenViewCache;
import io.github.zzih.rudder.mcp.capability.Capability;
import io.github.zzih.rudder.mcp.capability.CapabilityCatalog;
import io.github.zzih.rudder.service.workspace.event.WorkspaceMemberChangedEvent;

import java.util.List;
import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作空间成员变更 → MCP token 失效化。
 *
 * <p>双闸门里的 RBAC 闸已经能挡住降级后越权调用，但 grant 表如果不同步：
 * <ul>
 *   <li>前端 token 详情会一直显示"我有 capability X"，与实际可用能力不一致</li>
 *   <li>用户感知混乱，给排障添堵</li>
 * </ul>
 *
 * <p>策略：
 * <ul>
 *   <li>REMOVED → 整 token 撤销（已不再是 workspace 成员，token 失去归属）</li>
 *   <li>UPDATED → 检查每个 ACTIVE grant 的 capability.requiredRoles 是否仍含新角色，不含则单独 revoke</li>
 *   <li>ADDED → 不动（新加入对已有 token 无影响）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpRoleChangeListener {

    private final McpTokenDao tokenDao;
    private final McpTokenScopeGrantDao grantDao;
    private final McpTokenService tokenService;
    private final TokenViewCache tokenViewCache;

    @EventListener
    public void onMemberChanged(WorkspaceMemberChangedEvent event) {
        if (event.workspaceId() == null || event.userId() == null) {
            return;
        }
        List<McpToken> tokens = tokenDao.selectByUserId(event.userId()).stream()
                .filter(t -> Objects.equals(t.getWorkspaceId(), event.workspaceId()))
                .filter(t -> t.getStatus() == McpTokenStatus.ACTIVE)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        switch (event.type()) {
            case REMOVED -> revokeTokens(tokens, "USER_REMOVED_FROM_WORKSPACE");
            case UPDATED -> downgradeGrants(tokens, event.newRole());
            case ADDED -> {
                /* no-op */ }
            default -> {
                /* no-op */ }
        }
    }

    private void revokeTokens(List<McpToken> tokens, String reason) {
        for (McpToken t : tokens) {
            tokenService.revokeToken(t.getId(), reason);
        }
        log.info("MCP tokens revoked due to membership removal: count={}, reason={}",
                tokens.size(), reason);
    }

    private void downgradeGrants(List<McpToken> tokens, String newRoleName) {
        RoleType newRole = parseRole(newRoleName);
        if (newRole == null) {
            return;
        }
        int revoked = 0;
        for (McpToken t : tokens) {
            for (McpTokenScopeGrant g : grantDao.selectActiveByTokenId(t.getId())) {
                Capability cap = CapabilityCatalog.findById(g.getCapabilityId());
                if (cap == null || cap.isAllowedFor(newRole)) {
                    continue;
                }
                if (grantDao.revoke(g.getId(), "ROLE_DOWNGRADE_" + newRole.name()) > 0) {
                    revoked++;
                }
            }
            tokenViewCache.invalidateAll();
        }
        if (revoked > 0) {
            log.info("MCP grants revoked due to role downgrade: tokenCount={}, grantCount={}, newRole={}",
                    tokens.size(), revoked, newRole);
        }
    }

    private static RoleType parseRole(String roleName) {
        if (roleName == null) {
            return null;
        }
        try {
            return RoleType.of(roleName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
