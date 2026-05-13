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

import io.github.zzih.rudder.api.request.CreateMcpTokenRequest;
import io.github.zzih.rudder.api.response.CreateMcpTokenResponse;
import io.github.zzih.rudder.api.response.McpCapabilityItemResponse;
import io.github.zzih.rudder.api.response.McpClientGuideResponse;
import io.github.zzih.rudder.api.response.McpGrantInfoResponse;
import io.github.zzih.rudder.api.response.McpScopesAvailableResponse;
import io.github.zzih.rudder.api.response.McpTokenDetailResponse;
import io.github.zzih.rudder.api.response.McpTokenSummaryResponse;
import io.github.zzih.rudder.api.security.annotation.RequireLoggedIn;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.McpErrorCode;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.dto.CreateTokenCommand;
import io.github.zzih.rudder.mcp.auth.dto.CreateTokenResult;
import io.github.zzih.rudder.mcp.auth.dto.McpTokenSummary;
import io.github.zzih.rudder.mcp.capability.Capability;
import io.github.zzih.rudder.mcp.capability.CapabilityCatalog;
import io.github.zzih.rudder.mcp.client.McpClientGuideService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * MCP 管理面 Controller(走 JWT,不走 PAT)。前端 token 管理页 + 客户端连接指南页调这些 endpoint。
 *
 * <p>路径前缀 {@code /api/mcp/*}。注意 MCP 协议入口是 {@code /mcp}(由 Spring AI MCP server 接管 + PatAuthFilter
 * 守护),与本 controller 的管理面 endpoint 不重叠。
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
@RequireLoggedIn
public class McpController {

    private final McpTokenService tokenService;
    private final McpClientGuideService clientGuideService;

    @GetMapping("/capabilities")
    public Result<List<McpCapabilityItemResponse>> listAllCapabilities() {
        return Result.ok(BeanConvertUtils.convertList(
                CapabilityCatalog.allDTO(), McpCapabilityItemResponse.class));
    }

    @GetMapping("/scopes/available")
    public Result<McpScopesAvailableResponse> availableScopes(@RequestParam Long workspaceId) {
        Long userId = UserContext.requireUserId();
        RoleType role = tokenService.resolveRole(userId, workspaceId);
        if (role == null) {
            throw new AuthException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER, workspaceId);
        }
        McpScopesAvailableResponse resp = new McpScopesAvailableResponse();
        resp.setRole(role.name());
        resp.setCapabilities(BeanConvertUtils.convertList(
                CapabilityCatalog.availableForDTO(role), McpCapabilityItemResponse.class));
        return Result.ok(resp);
    }

    @GetMapping("/tokens")
    public Result<List<McpTokenSummaryResponse>> listMyTokens() {
        Long userId = UserContext.requireUserId();
        return Result.ok(BeanConvertUtils.convertList(
                tokenService.listByUserId(userId), McpTokenSummaryResponse.class));
    }

    @PostMapping("/tokens")
    public Result<CreateMcpTokenResponse> createToken(@RequestBody CreateMcpTokenRequest req) {
        Long userId = UserContext.requireUserId();
        if (req.getWorkspaceId() == null) {
            throw new BizException(McpErrorCode.WORKSPACE_ID_REQUIRED);
        }
        RoleType role = tokenService.resolveRole(userId, req.getWorkspaceId());
        if (role == null) {
            throw new AuthException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER, req.getWorkspaceId());
        }
        for (String capId : req.getCapabilities()) {
            Capability cap = CapabilityCatalog.requireById(capId);
            if (!cap.isAllowedFor(role)) {
                throw new AuthException(McpErrorCode.ROLE_NOT_ALLOWED_FOR_CAPABILITY, role.name(), capId);
            }
        }
        if (req.getExpiresInDays() <= 0 || req.getExpiresInDays() > 365) {
            throw new BizException(McpErrorCode.EXPIRES_IN_DAYS_INVALID);
        }

        CreateTokenResult result = tokenService.createToken(
                new CreateTokenCommand(
                        userId, req.getWorkspaceId(), req.getName(), req.getDescription(),
                        LocalDateTime.now().plusDays(req.getExpiresInDays()),
                        req.getCapabilities()));

        CreateMcpTokenResponse resp = new CreateMcpTokenResponse();
        resp.setTokenId(result.token().getId());
        resp.setPlainToken(result.plainToken());
        resp.setToken(BeanConvertUtils.convert(result.token(), McpTokenSummaryResponse.class));
        resp.setGrants(BeanConvertUtils.convertList(result.grants(), McpGrantInfoResponse.class));
        return Result.ok(resp);
    }

    @GetMapping("/tokens/{id}")
    public Result<McpTokenDetailResponse> getToken(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        McpTokenSummary t = tokenService.getById(id);
        if (t == null || !userId.equals(t.getUserId())) {
            throw new NotFoundException(McpErrorCode.TOKEN_NOT_FOUND, id);
        }
        McpTokenDetailResponse resp = new McpTokenDetailResponse();
        resp.setToken(BeanConvertUtils.convert(t, McpTokenSummaryResponse.class));
        resp.setGrants(BeanConvertUtils.convertList(tokenService.listGrants(id), McpGrantInfoResponse.class));
        return Result.ok(resp);
    }

    @DeleteMapping("/tokens/{id}")
    public Result<Void> revokeToken(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        McpTokenSummary t = tokenService.getById(id);
        if (t == null || !userId.equals(t.getUserId())) {
            throw new NotFoundException(McpErrorCode.TOKEN_NOT_FOUND, id);
        }
        tokenService.revokeToken(id, "USER_REVOKE");
        return Result.ok();
    }

    @GetMapping("/clients")
    public Result<List<McpClientGuideResponse>> listClients(
                                                            @RequestParam(value = "lang", required = false) String lang) {
        Locale locale = (lang == null || lang.isBlank()) ? Locale.CHINESE : Locale.forLanguageTag(lang);
        return Result.ok(BeanConvertUtils.convertList(
                clientGuideService.listAll(locale), McpClientGuideResponse.class));
    }
}
