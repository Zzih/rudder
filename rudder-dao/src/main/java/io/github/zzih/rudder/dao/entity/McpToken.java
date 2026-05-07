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

import io.github.zzih.rudder.common.entity.BaseEntity;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Personal Access Token。
 *
 * <p>token 强绑单 workspace —— 所有 tool 调用上下文 workspace_id 写死为本 token 的 workspace_id。
 * 明文 token 仅在创建时返回一次，DB 只存 bcrypt hash + 前缀（前缀做主查询，bcrypt 做最终验证）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_mcp_token")
public class McpToken extends BaseEntity {

    private Long userId;

    private Long workspaceId;

    private String name;

    private String description;

    /** {@code rdr_pat_xxxx} 前 12 字符，UNIQUE 索引主查询路径。 */
    private String tokenPrefix;

    /** bcrypt 全文 hash（cost=10）。 */
    private String tokenHash;

    private McpTokenStatus status;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    private String lastUsedIp;

    private LocalDateTime revokedAt;

    /** USER_REVOKE / ROLE_DOWNGRADE / EXPIRED / ADMIN_REVOKE */
    private String revokedReason;

    /** JOIN t_r_workspace 取出的关联名称,非持久列,仅 list/getById 路径回填。 */
    @TableField(exist = false)
    private String workspaceName;
}
