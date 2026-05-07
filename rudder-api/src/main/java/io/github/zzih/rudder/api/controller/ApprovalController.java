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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.request.ApprovalResolveRequest;
import io.github.zzih.rudder.api.request.ApprovalWithdrawRequest;
import io.github.zzih.rudder.api.response.ApprovalDecisionResponse;
import io.github.zzih.rudder.api.response.ApprovalRecordResponse;
import io.github.zzih.rudder.approval.api.model.ApprovalAction;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.ApprovalErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.page.PageRequest;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.ApprovalDecision;
import io.github.zzih.rudder.service.workflow.ApprovalService;
import io.github.zzih.rudder.service.workflow.dto.ApprovalRecordDTO;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@RequireRole(RoleType.VIEWER)
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    public Result<IPage<ApprovalRecordResponse>> page(
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String status) {
        IPage<ApprovalRecordDTO> page = approvalService.page(
                PageRequest.normalizePageNum(pageNum),
                PageRequest.normalizePageSize(pageSize), status);
        return Result.ok(BeanConvertUtils.convertPage(page, ApprovalRecordResponse.class));
    }

    @GetMapping("/{id}")
    public Result<ApprovalRecordResponse> getById(@PathVariable Long id) {
        ApprovalRecordDTO dto = approvalService.getById(id);
        if (dto == null) {
            return Result.ok(null);
        }
        ApprovalRecordResponse resp = BeanConvertUtils.convert(dto, ApprovalRecordResponse.class);
        resp.setStageChain(JsonUtils.toList(dto.getStageChain(), String.class));
        List<ApprovalDecision> decisions = approvalService.listDecisions(id);
        resp.setDecisions(BeanConvertUtils.convertList(decisions, ApprovalDecisionResponse.class));
        return Result.ok(resp);
    }

    @GetMapping("/resource")
    public Result<List<ApprovalRecordResponse>> listByResource(
                                                               @RequestParam String resourceType,
                                                               @RequestParam Long resourceCode) {
        return Result.ok(BeanConvertUtils.convertList(
                approvalService.listByResource(resourceType, resourceCode), ApprovalRecordResponse.class));
    }

    @PostMapping("/{id}/approve")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.APPROVAL, action = AuditAction.APPROVE, resourceType = AuditResourceType.APPROVAL_RECORD, resourceCode = "#id")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalResolveRequest request) {
        approvalService.decide(id, ApprovalAction.APPROVED,
                UserContext.requireUserId(),
                UserContext.requireUsername(),
                request != null ? request.getComment() : null);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.APPROVAL, action = AuditAction.REJECT, resourceType = AuditResourceType.APPROVAL_RECORD, resourceCode = "#id")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody ApprovalResolveRequest request) {
        if (request.getComment() == null || request.getComment().isBlank()) {
            throw new BizException(ApprovalErrorCode.REJECT_REASON_REQUIRED);
        }
        approvalService.decide(id, ApprovalAction.REJECTED,
                UserContext.requireUserId(),
                UserContext.requireUsername(),
                request.getComment());
        return Result.ok();
    }

    @PostMapping("/{id}/withdraw")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.APPROVAL, action = AuditAction.WITHDRAW, resourceType = AuditResourceType.APPROVAL_RECORD, resourceCode = "#id")
    public Result<Void> withdraw(@PathVariable Long id,
                                 @RequestBody(required = false) ApprovalWithdrawRequest request) {
        approvalService.withdraw(id, UserContext.requireUserId(),
                request != null ? request.getReason() : null);
        return Result.ok();
    }
}
