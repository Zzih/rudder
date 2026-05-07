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

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.McpErrorCode;
import io.github.zzih.rudder.common.enums.mcp.McpScopeGrantStatus;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.McpTokenDao;
import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.McpToken;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.mcp.auth.dto.CreateTokenCommand;
import io.github.zzih.rudder.mcp.auth.dto.CreateTokenResult;
import io.github.zzih.rudder.mcp.auth.dto.McpGrantInfo;
import io.github.zzih.rudder.mcp.auth.dto.McpTokenSummary;
import io.github.zzih.rudder.mcp.capability.Capability;
import io.github.zzih.rudder.mcp.capability.CapabilityCatalog;
import io.github.zzih.rudder.mcp.capability.RwClass;
import io.github.zzih.rudder.service.workflow.ApprovalService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP PAT 生命周期：创建 / 列表 / 撤销 / 验证。
 *
 * <p>明文 token 仅在 {@link #createToken} 返回值里出现一次，DB 只存 bcrypt hash + 前缀。
 * 验证路径优先走 {@link TokenViewCache}（5s TTL），cache miss 时跑 bcrypt + DB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpTokenService {

    private final McpTokenDao tokenDao;
    private final McpTokenScopeGrantDao grantDao;
    private final WorkspaceMemberDao workspaceMemberDao;
    private final TokenViewCache tokenViewCache;
    private final ApprovalService approvalService;

    /** bcrypt cost=10 — 单次验证约 80-100ms，靠 TokenViewCache 避热路径。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public CreateTokenResult createToken(CreateTokenCommand req) {
        if (req.capabilityIds() == null || req.capabilityIds().isEmpty()) {
            throw new BizException(McpErrorCode.AT_LEAST_ONE_CAPABILITY_REQUIRED);
        }
        // 校验 capability 存在
        List<Capability> caps = req.capabilityIds().stream()
                .map(CapabilityCatalog::requireById)
                .toList();

        String plainToken = PatCodec.generate();
        String prefix = PatCodec.prefixOf(plainToken);
        String hash = passwordEncoder.encode(plainToken);

        McpToken token = new McpToken();
        token.setUserId(req.userId());
        token.setWorkspaceId(req.workspaceId());
        token.setName(req.name());
        token.setDescription(req.description());
        token.setTokenPrefix(prefix);
        token.setTokenHash(hash);
        token.setStatus(McpTokenStatus.ACTIVE);
        token.setExpiresAt(req.expiresAt());
        token.setCreatedBy(req.userId());
        tokenDao.insert(token);

        boolean isSuperAdmin = UserContext.isSuperAdmin();
        List<Capability> writeCaps = caps.stream()
                .filter(c -> c.rwClass() == RwClass.WRITE)
                .toList();
        Long sharedApprovalId = (!writeCaps.isEmpty() && !isSuperAdmin)
                ? submitWriteApproval(token, req, writeCaps)
                : null;

        List<McpTokenScopeGrant> grants = new ArrayList<>(caps.size());
        LocalDateTime now = LocalDateTime.now();
        for (Capability c : caps) {
            McpTokenScopeGrant g = new McpTokenScopeGrant();
            g.setTokenId(token.getId());
            g.setCapabilityId(c.id());
            g.setRwClass(c.rwClass().name());
            if (c.rwClass() == RwClass.READ || isSuperAdmin) {
                g.setStatus(McpScopeGrantStatus.ACTIVE);
                g.setActivatedAt(now);
            } else {
                g.setStatus(McpScopeGrantStatus.PENDING_APPROVAL);
                g.setApprovalId(sharedApprovalId);
            }
            grants.add(g);
        }
        grantDao.batchInsert(grants);

        log.info("MCP token created: id={}, userId={}, workspaceId={}, prefix={}, "
                + "capabilities={}, writeCaps={}, approvalId={}",
                token.getId(), req.userId(), req.workspaceId(), prefix,
                req.capabilityIds(), writeCaps.size(), sharedApprovalId);
        return new CreateTokenResult(
                BeanConvertUtils.convert(token, McpTokenSummary.class),
                plainToken,
                grants.stream().map(this::toGrantInfo).toList());
    }

    private McpGrantInfo toGrantInfo(McpTokenScopeGrant g) {
        McpGrantInfo info = BeanConvertUtils.convert(g, McpGrantInfo.class);
        // entity.capabilityId → DTO.capability，字段名 mismatch 单独赋值
        info.setCapability(g.getCapabilityId());
        return info;
    }

    /**
     * 一个 token 申请的全部 WRITE capability 合并成一份审批单 —— owner 一次决议覆盖所有 grant。
     *
     * <p>content 末尾埋一行机器可读标记 {@code <!-- mcp-cap:<id1>,<id2>,... -->}，让
     * {@code McpTokenStageFlow} 解析后按"最高敏感度"决定单级 / 二级审批链 —— 任意一个是 HIGH 即走双级。
     */
    private Long submitWriteApproval(McpToken token, CreateTokenCommand req, List<Capability> writeCaps) {
        String expiresAt = req.expiresAt() != null ? req.expiresAt().toString() : "N/A";
        StringBuilder content = new StringBuilder();
        content.append("Request write permission for MCP token \"").append(req.name()).append("\":\n");
        for (Capability c : writeCaps) {
            content.append("  • ").append(c.id()).append(" — ").append(I18n.t(c.description())).append('\n');
        }
        content.append("  • Token prefix: ").append(token.getTokenPrefix()).append("...\n");
        content.append("  • Workspace ID: ").append(token.getWorkspaceId()).append('\n');
        content.append("  • Expires at: ").append(expiresAt).append('\n');
        String capList = writeCaps.stream().map(Capability::id).collect(java.util.stream.Collectors.joining(","));
        content.append("<!-- mcp-cap:").append(capList).append(" -->");

        String title = writeCaps.size() == 1
                ? "[MCP] " + req.name() + " · " + writeCaps.get(0).id()
                : "[MCP] " + req.name() + " · " + writeCaps.size() + " write scopes";

        ApprovalRequest request = ApprovalRequest.builder()
                .title(title)
                .content(content.toString())
                .build();
        return approvalService.submit(
                request,
                ApprovalResourceType.MCP_TOKEN,
                token.getId(),
                req.workspaceId(),
                null,
                "Request MCP token write permission: " + capList);
    }

    /** 撤销：token + 所有 grants 级联撤销 + 缓存失效。 */
    @Transactional
    public void revokeToken(Long tokenId, String reason) {
        McpToken token = tokenDao.selectById(tokenId);
        if (token == null) {
            throw new NotFoundException(McpErrorCode.TOKEN_NOT_FOUND, tokenId);
        }
        int rows = tokenDao.revokeIfActive(tokenId, reason);
        if (rows == 0) {
            log.info("MCP token already non-active, skip revoke: id={}, status={}",
                    tokenId, token.getStatus());
            return;
        }
        grantDao.revokeAllByTokenId(tokenId, "TOKEN_REVOKED");
        tokenViewCache.invalidateAll();
        log.info("MCP token revoked: id={}, reason={}", tokenId, reason);
    }

    public List<McpTokenSummary> listByUserId(Long userId) {
        return BeanConvertUtils.convertList(tokenDao.selectByUserId(userId), McpTokenSummary.class);
    }

    public List<McpTokenSummary> listByWorkspaceId(Long workspaceId) {
        return BeanConvertUtils.convertList(tokenDao.selectByWorkspaceId(workspaceId), McpTokenSummary.class);
    }

    public McpTokenSummary getById(Long id) {
        McpToken t = tokenDao.selectByIdWithWorkspaceName(id);
        return t == null ? null : BeanConvertUtils.convert(t, McpTokenSummary.class);
    }

    public List<McpGrantInfo> listGrants(Long tokenId) {
        return grantDao.selectByTokenId(tokenId).stream().map(this::toGrantInfo).toList();
    }

    /**
     * 解析当前用户在 token 绑定 workspace 的角色（双闸门 RBAC 的输入）。
     *
     * <p>优先用 {@link UserContext} 上已有的角色（filter 层注入，无 DB 开销）；
     * 不存在时再走 {@code workspace_member} 表查询。
     */
    public RoleType resolveRole(Long userId, Long workspaceId) {
        String roleName = UserContext.getRole();
        if (roleName == null || roleName.isBlank()) {
            WorkspaceMember m = workspaceMemberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
            if (m == null) {
                return null;
            }
            roleName = m.getRole();
        }
        try {
            return RoleType.of(roleName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 验证明文 token。命中 cache 走快路径，否则 bcrypt + DB 回填。
     *
     * @return token 视图（含 active capabilities）；token 不存在 / 已撤销 / 过期 / hash 不匹配 → empty
     */
    public Optional<TokenView> verify(String plainToken) {
        if (!PatCodec.isWellFormed(plainToken)) {
            return Optional.empty();
        }
        // 快路径：cache 命中直接返回
        TokenView cached = tokenViewCache.getIfPresent(plainToken);
        if (cached != null) {
            if (cached.isExpired() || cached.status() != McpTokenStatus.ACTIVE) {
                tokenViewCache.invalidate(plainToken);
                return Optional.empty();
            }
            return Optional.of(cached);
        }
        // 慢路径：DB + bcrypt
        String prefix = PatCodec.prefixOf(plainToken);
        McpToken token = tokenDao.selectByTokenPrefix(prefix);
        if (token == null) {
            return Optional.empty();
        }
        if (token.getStatus() != McpTokenStatus.ACTIVE) {
            return Optional.empty();
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(plainToken, token.getTokenHash())) {
            log.warn("MCP token bcrypt mismatch: prefix={}, tokenId={}", prefix, token.getId());
            return Optional.empty();
        }
        // 加载 ACTIVE capabilities
        Set<String> activeCaps = new HashSet<>();
        for (McpTokenScopeGrant g : grantDao.selectActiveByTokenId(token.getId())) {
            activeCaps.add(g.getCapabilityId());
        }
        TokenView view = new TokenView(
                token.getId(), token.getUserId(), token.getWorkspaceId(),
                token.getStatus(), token.getExpiresAt(), activeCaps);
        tokenViewCache.put(plainToken, view);
        return Optional.of(view);
    }
}
