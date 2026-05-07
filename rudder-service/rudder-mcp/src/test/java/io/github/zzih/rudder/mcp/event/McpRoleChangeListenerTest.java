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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;
import io.github.zzih.rudder.dao.dao.McpTokenDao;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpToken;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.TokenViewCache;
import io.github.zzih.rudder.mcp.capability.CapabilityIds;
import io.github.zzih.rudder.service.workspace.event.WorkspaceMemberChangedEvent;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpRoleChangeListenerTest {

    @Mock
    private McpTokenDao tokenDao;
    @Mock
    private McpTokenScopeGrantDao grantDao;
    @Mock
    private McpTokenService tokenService;
    @Mock
    private TokenViewCache tokenViewCache;

    @InjectMocks
    private McpRoleChangeListener listener;

    private static McpToken activeToken(Long id, Long workspaceId) {
        McpToken t = new McpToken();
        t.setId(id);
        t.setWorkspaceId(workspaceId);
        t.setStatus(McpTokenStatus.ACTIVE);
        return t;
    }

    private static McpTokenScopeGrant grant(Long id, String capabilityId) {
        McpTokenScopeGrant g = new McpTokenScopeGrant();
        g.setId(id);
        g.setCapabilityId(capabilityId);
        return g;
    }

    @Test
    @DisplayName("REMOVED → 该 user 在该 workspace 的全部 ACTIVE token 被 revoke")
    void removedRevokesAllTokens() {
        when(tokenDao.selectByUserId(7L)).thenReturn(List.of(
                activeToken(1L, 5L),
                activeToken(2L, 5L),
                activeToken(3L, 99L))); // 别 workspace 不动

        listener.onMemberChanged(WorkspaceMemberChangedEvent.removed(5L, 7L, "DEVELOPER"));

        verify(tokenService).revokeToken(1L, "USER_REMOVED_FROM_WORKSPACE");
        verify(tokenService).revokeToken(2L, "USER_REMOVED_FROM_WORKSPACE");
        verify(tokenService, never()).revokeToken(eq(3L), any());
    }

    @Test
    @DisplayName("UPDATED 降级到 VIEWER → execution.run 等 cap 被单独 revoke，metadata.browse 保留")
    void updatedDowngradesGrants() {
        when(tokenDao.selectByUserId(7L)).thenReturn(List.of(activeToken(1L, 5L)));
        when(grantDao.selectActiveByTokenId(1L)).thenReturn(List.of(
                grant(101L, CapabilityIds.METADATA_BROWSE), // VIEWER 仍允许
                grant(102L, CapabilityIds.EXECUTION_RUN), // VIEWER 不允许
                grant(103L, CapabilityIds.WORKFLOW_PUBLISH))); // VIEWER 不允许
        when(grantDao.revoke(anyLong(), any())).thenReturn(1);

        listener.onMemberChanged(WorkspaceMemberChangedEvent.updated(
                5L, 7L, RoleType.DEVELOPER.name(), RoleType.VIEWER.name()));

        verify(grantDao, never()).revoke(eq(101L), any());
        verify(grantDao).revoke(eq(102L), eq("ROLE_DOWNGRADE_VIEWER"));
        verify(grantDao).revoke(eq(103L), eq("ROLE_DOWNGRADE_VIEWER"));
        verify(tokenViewCache).invalidateAll();
    }

    @Test
    @DisplayName("UPDATED 提升到 SUPER_ADMIN → 不撤销任何 grant（角色更高）")
    void updatedUpgradeNoRevoke() {
        when(tokenDao.selectByUserId(7L)).thenReturn(List.of(activeToken(1L, 5L)));
        when(grantDao.selectActiveByTokenId(1L)).thenReturn(List.of(
                grant(101L, CapabilityIds.EXECUTION_RUN)));

        listener.onMemberChanged(WorkspaceMemberChangedEvent.updated(
                5L, 7L, RoleType.DEVELOPER.name(), RoleType.SUPER_ADMIN.name()));

        verify(grantDao, never()).revoke(anyLong(), any());
        verify(tokenViewCache, times(1)).invalidateAll(); // 仍主动 invalidate 以反映新 role
    }

    @Test
    @DisplayName("ADDED → 不动")
    void addedNoOp() {
        when(tokenDao.selectByUserId(7L)).thenReturn(List.of()); // 即使有也不应被处理
        listener.onMemberChanged(WorkspaceMemberChangedEvent.added(5L, 7L, "DEVELOPER"));
        verify(tokenService, never()).revokeToken(anyLong(), any());
        verify(grantDao, never()).revoke(anyLong(), any());
    }

    @Test
    @DisplayName("空 user / 空 workspace → 早 return")
    void nullSafeguard() {
        listener.onMemberChanged(new WorkspaceMemberChangedEvent(
                null, 7L, "DEVELOPER", "VIEWER",
                WorkspaceMemberChangedEvent.ChangeType.UPDATED));
        listener.onMemberChanged(new WorkspaceMemberChangedEvent(
                5L, null, "DEVELOPER", "VIEWER",
                WorkspaceMemberChangedEvent.ChangeType.UPDATED));
        verify(tokenDao, never()).selectByUserId(anyLong());
    }
}
