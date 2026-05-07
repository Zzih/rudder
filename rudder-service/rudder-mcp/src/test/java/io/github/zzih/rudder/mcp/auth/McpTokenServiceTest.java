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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.mcp.McpScopeGrantStatus;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;
import io.github.zzih.rudder.dao.dao.McpTokenDao;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpToken;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.mcp.auth.dto.CreateTokenCommand;
import io.github.zzih.rudder.mcp.capability.CapabilityIds;
import io.github.zzih.rudder.service.workflow.ApprovalService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpTokenServiceTest {

    @Mock
    private McpTokenDao tokenDao;
    @Mock
    private McpTokenScopeGrantDao grantDao;
    @Mock
    private TokenViewCache tokenViewCache;
    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private McpTokenService service;

    /** 模拟 DAO insert 后回填 id —— 仅 createToken 系列测试调用。 */
    private void stubInsertReturnsId() {
        org.mockito.Mockito.doAnswer(inv -> {
            McpToken t = inv.getArgument(0);
            t.setId(7L);
            return null;
        }).when(tokenDao).insert(any(McpToken.class));
    }

    @Test
    @DisplayName("createToken: READ-only → grant 全部 ACTIVE，不调审批")
    void createReadOnlyTokenSkipsApproval() {
        stubInsertReturnsId();
        var req = new CreateTokenCommand(
                100L, 5L, "claude-laptop", null,
                LocalDateTime.now().plusDays(30),
                List.of(CapabilityIds.METADATA_BROWSE, CapabilityIds.SCRIPT_BROWSE));

        var result = service.createToken(req);

        assertThat(result.plainToken()).startsWith("rdr_pat_");
        assertThat(result.token().getId()).isEqualTo(7L);
        assertThat(result.token().getStatus()).isEqualTo(McpTokenStatus.ACTIVE);

        ArgumentCaptor<List<McpTokenScopeGrant>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(grantDao).batchInsert(captor.capture());
        assertThat(captor.getValue())
                .allMatch(g -> g.getStatus() == McpScopeGrantStatus.ACTIVE);

        verify(approvalService, never()).submit(any(), anyString(), anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("createToken: 含多个 WRITE → 合并成一份审批 + 全部 grant 共享 approvalId")
    void createWriteTokenSubmitsSingleApproval() {
        stubInsertReturnsId();
        when(approvalService.submit(any(), eq(ApprovalResourceType.MCP_TOKEN), eq(7L),
                eq(5L), any(), anyString())).thenReturn(42L);

        var req = new CreateTokenCommand(
                100L, 5L, "claude-laptop", null,
                LocalDateTime.now().plusDays(30),
                List.of(CapabilityIds.METADATA_BROWSE, // READ → ACTIVE
                        CapabilityIds.EXECUTION_RUN, // WRITE → 共享审批
                        CapabilityIds.WORKFLOW_PUBLISH)); // WRITE → 共享审批

        var result = service.createToken(req);

        ArgumentCaptor<List<McpTokenScopeGrant>> grantCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(grantDao).batchInsert(grantCaptor.capture());
        List<McpTokenScopeGrant> grants = grantCaptor.getValue();
        assertThat(grants).hasSize(3);
        assertThat(grants.stream().filter(g -> g.getStatus() == McpScopeGrantStatus.ACTIVE))
                .hasSize(1);

        // 多个 WRITE capability 合并提一次审批
        verify(approvalService, times(1)).submit(any(), eq(ApprovalResourceType.MCP_TOKEN),
                eq(7L), eq(5L), any(), anyString());

        // 全部 WRITE grant 共享同一个 approvalId；READ grant 不该有 approvalId
        var writeGrants = grants.stream()
                .filter(g -> g.getStatus() == McpScopeGrantStatus.PENDING_APPROVAL)
                .toList();
        assertThat(writeGrants).hasSize(2)
                .allMatch(g -> g.getApprovalId() != null && g.getApprovalId() == 42L);
        assertThat(grants.stream().filter(g -> g.getStatus() == McpScopeGrantStatus.ACTIVE))
                .allMatch(g -> g.getApprovalId() == null);

        assertThat(result.plainToken()).startsWith("rdr_pat_");
    }

    @Test
    @DisplayName("createToken: SUPER_ADMIN 申请 → 全部 grant 直接 ACTIVE，不发审批")
    void createTokenAsSuperAdminBypassesApproval() {
        stubInsertReturnsId();
        var req = new CreateTokenCommand(
                100L, 5L, "admin-cli", null,
                LocalDateTime.now().plusDays(30),
                List.of(CapabilityIds.METADATA_BROWSE,
                        CapabilityIds.EXECUTION_RUN, // WRITE + HIGH
                        CapabilityIds.WORKFLOW_PUBLISH)); // WRITE + HIGH

        var admin = new UserContext.UserInfo(100L, "admin", 5L, null, RoleType.SUPER_ADMIN.name());
        UserContext.runWith(admin, () -> service.createToken(req));

        ArgumentCaptor<List<McpTokenScopeGrant>> grantCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(grantDao).batchInsert(grantCaptor.capture());
        List<McpTokenScopeGrant> grants = grantCaptor.getValue();
        assertThat(grants).hasSize(3)
                .allMatch(g -> g.getStatus() == McpScopeGrantStatus.ACTIVE)
                .allMatch(g -> g.getApprovalId() == null);

        verify(approvalService, never()).submit(any(), anyString(), anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("createToken: 空 capabilityIds → 抛 IllegalArgumentException")
    void createTokenRejectsEmptyCapabilities() {
        var req = new CreateTokenCommand(100L, 5L, "x", null,
                LocalDateTime.now().plusDays(1), List.of());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createToken(req));
        verify(tokenDao, never()).insert(any());
    }

    @Test
    @DisplayName("revokeToken: ACTIVE token → 走撤销 + 级联 grant + 缓存失效")
    void revokeActiveToken() {
        McpToken t = new McpToken();
        t.setId(7L);
        t.setStatus(McpTokenStatus.ACTIVE);
        when(tokenDao.selectById(7L)).thenReturn(t);
        when(tokenDao.revokeIfActive(eq(7L), anyString())).thenReturn(1);

        service.revokeToken(7L, "USER_REVOKE");

        verify(grantDao).revokeAllByTokenId(eq(7L), eq("TOKEN_REVOKED"));
        verify(tokenViewCache).invalidateAll();
    }

    @Test
    @DisplayName("revokeToken: 已 REVOKED → 跳过级联")
    void revokeAlreadyRevoked() {
        McpToken t = new McpToken();
        t.setId(7L);
        t.setStatus(McpTokenStatus.REVOKED);
        when(tokenDao.selectById(7L)).thenReturn(t);
        when(tokenDao.revokeIfActive(eq(7L), anyString())).thenReturn(0);

        service.revokeToken(7L, "USER_REVOKE");

        verify(grantDao, never()).revokeAllByTokenId(anyLong(), anyString());
        verify(tokenViewCache, never()).invalidateAll();
    }

    @Test
    @DisplayName("verify: cache 命中 ACTIVE → 直接返回 view，不查 DB")
    void verifyHitsCache() {
        TokenView cached = new TokenView(7L, 100L, 5L, McpTokenStatus.ACTIVE,
                LocalDateTime.now().plusDays(1), Set.of(CapabilityIds.METADATA_BROWSE));
        when(tokenViewCache.getIfPresent("rdr_pat_xxxxxx")).thenReturn(cached);

        var view = service.verify("rdr_pat_xxxxxx");
        assertThat(view).isPresent();
        verify(tokenDao, never()).selectByTokenPrefix(anyString());
    }

    @Test
    @DisplayName("verify: 格式不合法 → empty，不查任何后端")
    void verifyMalformedToken() {
        assertThat(service.verify("not_a_pat")).isEmpty();
        verify(tokenViewCache, never()).getIfPresent(anyString());
        verify(tokenDao, never()).selectByTokenPrefix(anyString());
    }
}
