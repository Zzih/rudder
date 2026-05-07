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

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.mcp.capability.Capability;
import io.github.zzih.rudder.mcp.capability.CapabilityCatalog;

import org.springframework.stereotype.Component;

/**
 * MCP 双闸门 — 每次 tool 调用必须双闸通过。
 *
 * <ol>
 *   <li><b>Scope 闸</b>：tool 所需 capability ∈ token 的 ACTIVE scope_grant
 *       （PENDING/REJECTED/REVOKED 不算）</li>
 *   <li><b>RBAC 闸</b>：当前用户在该 workspace 的角色仍允许该 capability
 *       （角色被降级时立即收紧）</li>
 * </ol>
 *
 * <p>能力 = ACTIVE scope ∩ 当前角色能力 — 两者任一缩水即拒绝。
 */
@Component
public class ScopeChecker {

    /**
     * 检查授权结果。
     */
    public enum Decision {
        /** 通过，可以执行。 */
        ALLOW,
        /** 拒绝：token 没有该 capability 的 scope（未申请 / 撤销 / 拒绝）。 */
        DENIED_SCOPE,
        /** 拒绝：当前用户角色不允许此 capability（角色被降级 / 错误 token 借用）。 */
        DENIED_RBAC,
        /** 拒绝：未知 capability（tool 注册了但 catalog 没有）。 */
        DENIED_UNKNOWN_CAPABILITY
    }

    /**
     * 双闸门校验。
     *
     * @param tokenView   PAT 验证后的不可变快照
     * @param currentRole 当前用户在 token 绑定 workspace 的角色（可能与发 token 时不同）
     * @param capabilityId tool 声明的 capability id
     */
    public Decision check(TokenView tokenView, RoleType currentRole, String capabilityId) {
        Capability cap = CapabilityCatalog.findById(capabilityId);
        if (cap == null) {
            return Decision.DENIED_UNKNOWN_CAPABILITY;
        }
        if (tokenView == null || !tokenView.hasCapability(capabilityId)) {
            return Decision.DENIED_SCOPE;
        }
        if (!cap.isAllowedFor(currentRole)) {
            return Decision.DENIED_RBAC;
        }
        return Decision.ALLOW;
    }
}
