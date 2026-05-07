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

package io.github.zzih.rudder.mcp.tool.approval;

import io.github.zzih.rudder.approval.api.model.ApprovalAction;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.page.PageRequest;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.Page;
import io.github.zzih.rudder.service.workflow.ApprovalService;
import io.github.zzih.rudder.service.workflow.dto.ApprovalRecordDTO;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/** Approval 域 MCP tools 聚合（view + act）。 */
@Service
@RequiredArgsConstructor
public class ApprovalMcpTools {

    private final ApprovalService approvalService;

    /** 审批列表项 — 投影 7 个字段，不含完整 content / 历史步骤等大字段。 */
    public record ApprovalSummary(
            Long id,
            String title,
            String resourceType,
            Long resourceCode,
            String status,
            String currentStage,
            String createdAt) {
    }

    /** 决议结果。 */
    public record DecideResult(boolean ok, Long id, String action) {
    }

    @McpTool(name = "approval.list", description = "List approval records visible to the current user (filter by status optional).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("approval.view")
    public Page<ApprovalSummary> list(
                                      @McpToolParam(description = "page number (1-based)") Integer pageNum,
                                      @McpToolParam(description = "page size (default 20, max 100)") Integer pageSize,
                                      @McpToolParam(description = "status filter (PENDING/APPROVED/REJECTED/...)") String status) {
        int p = PageRequest.normalizePageNum(pageNum == null ? 1 : pageNum);
        int s = PageRequest.normalizePageSize(pageSize == null ? 20 : pageSize);
        IPage<ApprovalRecordDTO> page = approvalService.page(p, s, status);
        List<ApprovalSummary> rows = page.getRecords().stream()
                .map(ApprovalMcpTools::toSummary)
                .toList();
        return Page.of(page.getTotal(), p, s, rows);
    }

    @McpTool(name = "approval.decide", description = "Decide an approval (APPROVE or REJECT) — caller must be a current-stage candidate.", annotations = @McpTool.McpAnnotations(destructiveHint = true))
    @McpCapability("approval.act")
    public DecideResult decide(
                               @McpToolParam(description = "approval id", required = true) Long id,
                               @McpToolParam(description = "APPROVE or REJECT", required = true) String action,
                               @McpToolParam(description = "decision comment") String comment) {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        ApprovalAction a = parseAction(action);
        Long deciderUserId = UserContext.requireUserId();
        String username = UserContext.getUsername();
        if (username == null || username.isBlank()) {
            username = "user-" + deciderUserId;
        }
        approvalService.decide(id, a, deciderUserId, username, comment);
        return new DecideResult(true, id, a.name());
    }

    private static ApprovalAction parseAction(String action) {
        if (action == null) {
            throw new IllegalArgumentException("action required (APPROVE or REJECT)");
        }
        try {
            return ApprovalAction.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("action must be APPROVE or REJECT, got: " + action);
        }
    }

    private static ApprovalSummary toSummary(ApprovalRecordDTO r) {
        return new ApprovalSummary(
                r.getId(),
                r.getTitle(),
                r.getResourceType(),
                r.getResourceCode(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getCurrentStage(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    }
}
