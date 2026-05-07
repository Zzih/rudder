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

import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.mcp.auth.TokenViewCache;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批终态 → MCP grant 状态推进。
 *
 * <p>仅消费 resourceType={@code MCP_TOKEN} 的事件。
 * <ul>
 *   <li>APPROVED → 把该审批关联的全部 PENDING_APPROVAL grant 激活为 ACTIVE</li>
 *   <li>REJECTED / WITHDRAWN / EXPIRED → 全部 grant 标记 REJECTED</li>
 * </ul>
 *
 * <p>使用 {@link TransactionalEventListener#phase()}=AFTER_COMMIT 确保 ApprovalService
 * 主事务提交后再处理；否则 finalizeIfPending 还没落库时 listener 看到的是旧状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpApprovalFinalizedListener {

    private final McpTokenScopeGrantDao grantDao;
    private final TokenViewCache tokenViewCache;

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinalized(ApprovalFinalizedEvent event) {
        if (!ApprovalResourceType.MCP_TOKEN.equals(event.resourceType())) {
            return;
        }
        List<McpTokenScopeGrant> grants = grantDao.selectByApprovalId(event.approvalId());
        if (grants.isEmpty()) {
            log.warn("MCP approval finalized but no grants linked: approvalId={}, status={}",
                    event.approvalId(), event.finalStatus());
            return;
        }

        Long tokenId = grants.get(0).getTokenId();
        if (event.isApproved()) {
            int activated = 0;
            for (McpTokenScopeGrant g : grants) {
                if (grantDao.activateIfPending(g.getId(), event.deciderUserId()) > 0) {
                    activated++;
                }
            }
            log.info("MCP grants activated: tokenId={}, approvalId={}, activated={}/{}",
                    tokenId, event.approvalId(), activated, grants.size());
        } else {
            String reason = "APPROVAL_" + event.finalStatus();
            int rejected = 0;
            for (McpTokenScopeGrant g : grants) {
                if (grantDao.rejectIfPending(g.getId(), event.deciderUserId(), reason) > 0) {
                    rejected++;
                }
            }
            log.info("MCP grants rejected: tokenId={}, approvalId={}, rejected={}/{}, reason={}",
                    tokenId, event.approvalId(), rejected, grants.size(), reason);
        }
        // grant 集合变化 → 缓存的 TokenView.activeCapabilities 可能过期，主动失效
        tokenViewCache.invalidateAll();
    }
}
