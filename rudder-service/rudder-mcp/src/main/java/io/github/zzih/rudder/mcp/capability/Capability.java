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

package io.github.zzih.rudder.mcp.capability;

import io.github.zzih.rudder.common.enums.auth.RoleType;

import java.util.Set;

/**
 * MCP capability 定义。
 *
 * <p>每个 capability 对应"用户在 UI 上能操作的一类功能"，是 MCP 的最小授权单元。
 * Tool 通过 {@code @McpTool(capability=...)} 关联到具体 capability，
 * Token 通过 scope_grant 表持久化用户被授权的 capability 集合。
 *
 * @param id            capability 唯一标识，如 {@code script.author}
 * @param domain        业务领域，如 script / datasource / execution
 * @param rwClass       读写类别（READ 直发，WRITE 走审批）
 * @param sensitivity   敏感度：NORMAL → 单级审批（WORKSPACE_OWNER），HIGH → 二级审批（WORKSPACE_OWNER + SUPER_ADMIN）
 * @param description   i18n key,形如 {@code capability.<id>.description};{@code toDTO} 出口按 locale resolve 成本地化文案
 * @param requiredRoles 必须满足以下任一角色才能拥有此 capability（运行时 RBAC 闸门）
 */
public record Capability(
        String id,
        String domain,
        RwClass rwClass,
        Sensitivity sensitivity,
        String description,
        Set<RoleType> requiredRoles) {

    public Capability {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("capability id required");
        }
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("capability domain required");
        }
        if (rwClass == null) {
            throw new IllegalArgumentException("rwClass required");
        }
        if (sensitivity == null) {
            sensitivity = Sensitivity.NORMAL;
        }
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            throw new IllegalArgumentException("at least one role required");
        }
        requiredRoles = Set.copyOf(requiredRoles);
    }

    /** 该 capability 是否对指定角色开放（RBAC 闸门用）。 */
    public boolean isAllowedFor(RoleType role) {
        return role != null && requiredRoles.contains(role);
    }

    /** 敏感度。READ capability 永远 NORMAL（无审批）；WRITE 按风险分级。 */
    public enum Sensitivity {

        /** 普通写：单级审批（工作空间 owner）。 */
        NORMAL,

        /** 高敏写：二级审批（工作空间 owner → 平台管理员复核）。 */
        HIGH
    }
}
