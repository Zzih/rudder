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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.enums.mcp.McpScopeGrantStatus;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * MCP token scope 授权（每 capability 一行）。
 *
 * <p>READ scope 创建时直接 status=ACTIVE；WRITE scope 创建时 status=PENDING_APPROVAL，
 * 关联 ApprovalRecord（resource_type=MCP_TOKEN）走 workspace owner 审批。
 *
 * <p>状态迁移走乐观锁 {@code WHERE status=?}，避免审批通过与用户撤销并发竞态。
 */
@Data
@TableName("t_r_mcp_token_scope_grant")
public class McpTokenScopeGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tokenId;

    private String capabilityId;

    /** READ / WRITE */
    private String rwClass;

    private McpScopeGrantStatus status;

    private Long approvalId;

    private LocalDateTime activatedAt;

    private Long activatedByUserId;

    private LocalDateTime rejectedAt;

    private Long rejectedByUserId;

    private String rejectedReason;

    private LocalDateTime revokedAt;

    /** ROLE_DOWNGRADE / TOKEN_REVOKED / USER_WITHDRAW */
    private String revokedReason;

    private LocalDateTime createdAt;
}
