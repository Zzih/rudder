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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.mcp.auth.TokenViewCache;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpApprovalFinalizedListenerTest {

    @Mock
    private McpTokenScopeGrantDao grantDao;

    @Mock
    private TokenViewCache tokenViewCache;

    @InjectMocks
    private McpApprovalFinalizedListener listener;

    private List<McpTokenScopeGrant> grants;

    @BeforeEach
    void setUp() {
        McpTokenScopeGrant g1 = new McpTokenScopeGrant();
        g1.setId(101L);
        g1.setTokenId(7L);
        g1.setCapabilityId("execution.run");
        McpTokenScopeGrant g2 = new McpTokenScopeGrant();
        g2.setId(102L);
        g2.setTokenId(7L);
        g2.setCapabilityId("workflow.publish");
        grants = List.of(g1, g2);
    }

    @Test
    @DisplayName("非 MCP_TOKEN 资源类型 → no-op，不查 DAO")
    void ignoreNonMcpResource() {
        ApprovalFinalizedEvent evt = new ApprovalFinalizedEvent(
                999L, "PROJECT_PUBLISH", 1L, "APPROVED", 5L, 88L);
        listener.onFinalized(evt);
        verify(grantDao, never()).selectByApprovalId(anyLong());
        verify(tokenViewCache, never()).invalidateAll();
    }

    @Test
    @DisplayName("APPROVED → 全部 PENDING grant 激活 + 缓存失效")
    void approvedActivatesAllGrants() {
        when(grantDao.selectByApprovalId(42L)).thenReturn(grants);
        when(grantDao.activateIfPending(eq(101L), eq(88L))).thenReturn(1);
        when(grantDao.activateIfPending(eq(102L), eq(88L))).thenReturn(1);

        listener.onFinalized(new ApprovalFinalizedEvent(
                42L, ApprovalResourceType.MCP_TOKEN, 7L,
                ApprovalFinalizedEvent.STATUS_APPROVED, 5L, 88L));

        verify(grantDao, times(2)).activateIfPending(anyLong(), eq(88L));
        verify(grantDao, never()).rejectIfPending(anyLong(), any(), anyString());
        verify(tokenViewCache).invalidateAll();
    }

    @Test
    @DisplayName("REJECTED → 全部 PENDING grant reject + 缓存失效")
    void rejectedRejectsAllGrants() {
        when(grantDao.selectByApprovalId(42L)).thenReturn(grants);

        listener.onFinalized(new ApprovalFinalizedEvent(
                42L, ApprovalResourceType.MCP_TOKEN, 7L,
                ApprovalFinalizedEvent.STATUS_REJECTED, 5L, 99L));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(grantDao, times(2)).rejectIfPending(anyLong(), eq(99L), reason.capture());
        assertThat(reason.getValue()).isEqualTo("APPROVAL_REJECTED");
        verify(grantDao, never()).activateIfPending(anyLong(), anyLong());
        verify(tokenViewCache).invalidateAll();
    }

    @Test
    @DisplayName("WITHDRAWN / EXPIRED → 当作负向终态处理")
    void withdrawnAndExpiredAreNegative() {
        when(grantDao.selectByApprovalId(43L)).thenReturn(grants);
        listener.onFinalized(new ApprovalFinalizedEvent(
                43L, ApprovalResourceType.MCP_TOKEN, 7L,
                ApprovalFinalizedEvent.STATUS_WITHDRAWN, 5L, null));
        verify(grantDao, times(2)).rejectIfPending(anyLong(), any(), eq("APPROVAL_WITHDRAWN"));

        when(grantDao.selectByApprovalId(44L)).thenReturn(grants);
        listener.onFinalized(new ApprovalFinalizedEvent(
                44L, ApprovalResourceType.MCP_TOKEN, 7L,
                ApprovalFinalizedEvent.STATUS_EXPIRED, 5L, null));
        verify(grantDao, times(2)).rejectIfPending(anyLong(), any(), eq("APPROVAL_EXPIRED"));
    }

    @Test
    @DisplayName("approval 没关联 grant → 记 warn 不抛异常")
    void noLinkedGrants() {
        when(grantDao.selectByApprovalId(99L)).thenReturn(List.of());
        listener.onFinalized(new ApprovalFinalizedEvent(
                99L, ApprovalResourceType.MCP_TOKEN, 7L,
                ApprovalFinalizedEvent.STATUS_APPROVED, 5L, 1L));
        verify(grantDao, never()).activateIfPending(anyLong(), anyLong());
        verify(tokenViewCache, never()).invalidateAll();
    }
}
