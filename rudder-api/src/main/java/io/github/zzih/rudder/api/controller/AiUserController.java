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

import io.github.zzih.rudder.ai.context.ContextProfileService;
import io.github.zzih.rudder.ai.context.PinnedTableService;
import io.github.zzih.rudder.ai.dto.AiContextProfileDTO;
import io.github.zzih.rudder.ai.feedback.FeedbackService;
import io.github.zzih.rudder.api.request.AiContextProfileRequest;
import io.github.zzih.rudder.api.request.AiFeedbackRequest;
import io.github.zzih.rudder.api.request.AiPinTableRequest;
import io.github.zzih.rudder.api.response.AiContextProfileResponse;
import io.github.zzih.rudder.api.response.AiFeedbackResponse;
import io.github.zzih.rudder.api.response.AiPinnedTableResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI 面向登录用户的个人行为 / 个人配置聚合 controller:
 * <ul>
 *   <li>/api/ai/feedback —— 对 assistant 消息 👍/👎</li>
 *   <li>/api/ai/pinned-tables —— 固定表引用(USER/WORKSPACE 两 scope)</li>
 *   <li>/api/ai/context-profiles —— session / workspace 级 prompt profile</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiUserController {

    private final FeedbackService feedbackService;
    private final PinnedTableService pinnedTableService;
    private final ContextProfileService contextProfileService;

    // ==================== Feedback ====================

    @PostMapping("/feedback")
    @RequireRole(RoleType.VIEWER)
    public Result<AiFeedbackResponse> submitFeedback(@Valid @RequestBody AiFeedbackRequest request) {
        return Result.ok(BeanConvertUtils.convert(
                feedbackService.recordDetail(request.getMessageId(), UserContext.getUserId(),
                        request.getSignal(), request.getComment(), false),
                AiFeedbackResponse.class));
    }

    @GetMapping("/feedback/messages/{messageId}")
    @RequireRole(RoleType.VIEWER)
    public Result<List<AiFeedbackResponse>> listFeedback(@PathVariable Long messageId) {
        return Result.ok(BeanConvertUtils.convertList(
                feedbackService.listByMessageDetail(messageId), AiFeedbackResponse.class));
    }

    // ==================== Pinned tables ====================

    @GetMapping("/pinned-tables")
    @RequireRole(RoleType.DEVELOPER)
    public Result<IPage<AiPinnedTableResponse>> listPinned(
                                                           @RequestParam(defaultValue = "USER") String scope,
                                                           @RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        Long scopeId = resolveScopeId(scope);
        return Result.ok(BeanConvertUtils.convertPage(
                pinnedTableService.pageDetail(scope, scopeId, pageNum, pageSize),
                AiPinnedTableResponse.class));
    }

    @PostMapping("/pinned-tables")
    @RequireRole(RoleType.DEVELOPER)
    public Result<AiPinnedTableResponse> pin(@Valid @RequestBody AiPinTableRequest request) {
        String scope = request.getScope() == null ? "USER" : request.getScope();
        Long scopeId = resolveScopeId(scope);
        return Result.ok(BeanConvertUtils.convert(
                pinnedTableService.pinDetail(scope, scopeId, request.getDatasourceId(),
                        request.getDatabaseName(), request.getTableName(), request.getNote()),
                AiPinnedTableResponse.class));
    }

    @DeleteMapping("/pinned-tables/{id}")
    @RequireRole(RoleType.DEVELOPER)
    public Result<Void> unpinById(@PathVariable Long id) {
        pinnedTableService.unpinById(id);
        return Result.ok();
    }

    @DeleteMapping("/pinned-tables")
    @RequireRole(RoleType.DEVELOPER)
    public Result<Void> unpin(@Valid @RequestBody AiPinTableRequest request) {
        String scope = request.getScope() == null ? "USER" : request.getScope();
        Long scopeId = resolveScopeId(scope);
        pinnedTableService.unpin(scope, scopeId, request.getDatasourceId(),
                request.getDatabaseName(), request.getTableName());
        return Result.ok();
    }

    // ==================== Context profiles ====================

    @GetMapping("/context-profiles/{scope}/{scopeId}")
    @RequireRole(RoleType.DEVELOPER)
    public Result<AiContextProfileResponse> getContextProfile(@PathVariable String scope, @PathVariable Long scopeId) {
        return Result.ok(BeanConvertUtils.convert(
                contextProfileService.getDetail(scope, scopeId), AiContextProfileResponse.class));
    }

    @PutMapping("/context-profiles")
    @RequireRole(RoleType.DEVELOPER)
    public Result<AiContextProfileResponse> upsertContextProfile(@Valid @RequestBody AiContextProfileRequest request) {
        return Result.ok(BeanConvertUtils.convert(
                contextProfileService.upsertDetail(BeanConvertUtils.convert(request, AiContextProfileDTO.class)),
                AiContextProfileResponse.class));
    }

    @DeleteMapping("/context-profiles/{scope}/{scopeId}")
    @RequireRole(RoleType.DEVELOPER)
    public Result<Void> clearContextProfile(@PathVariable String scope, @PathVariable Long scopeId) {
        contextProfileService.clear(scope, scopeId);
        return Result.ok();
    }

    // ==================== helpers ====================

    private Long resolveScopeId(String scope) {
        if (PinnedTableService.SCOPE_WORKSPACE.equals(scope)) {
            return UserContext.requireWorkspaceId();
        }
        return UserContext.getUserId();
    }
}
