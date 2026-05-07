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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScopeCheckerTest {

    private final ScopeChecker checker = new ScopeChecker();

    private TokenView viewWith(String... activeCaps) {
        return new TokenView(1L, 100L, 42L, McpTokenStatus.ACTIVE,
                LocalDateTime.now().plusDays(1), Set.of(activeCaps));
    }

    @Test
    @DisplayName("ALLOW: token 有 scope + 角色允许")
    void allowWhenScopeAndRolePass() {
        TokenView view = viewWith("script.author");
        assertThat(checker.check(view, RoleType.DEVELOPER, "script.author"))
                .isEqualTo(ScopeChecker.Decision.ALLOW);
    }

    @Test
    @DisplayName("DENIED_SCOPE: token 没有该 capability")
    void deniedWhenScopeMissing() {
        TokenView view = viewWith("script.browse");
        assertThat(checker.check(view, RoleType.DEVELOPER, "script.author"))
                .isEqualTo(ScopeChecker.Decision.DENIED_SCOPE);
    }

    @Test
    @DisplayName("DENIED_RBAC: token 有 scope 但角色被降级，capability 不再允许")
    void deniedWhenRoleDowngraded() {
        TokenView view = viewWith("script.author");
        // VIEWER 角色不被允许 script.author
        assertThat(checker.check(view, RoleType.VIEWER, "script.author"))
                .isEqualTo(ScopeChecker.Decision.DENIED_RBAC);
    }

    @Test
    @DisplayName("DENIED_UNKNOWN_CAPABILITY: catalog 找不到 capability")
    void deniedForUnknownCapability() {
        TokenView view = viewWith("anything");
        assertThat(checker.check(view, RoleType.SUPER_ADMIN, "made.up"))
                .isEqualTo(ScopeChecker.Decision.DENIED_UNKNOWN_CAPABILITY);
    }

    @Test
    @DisplayName("DENIED_SCOPE: tokenView=null（验证失败路径）")
    void deniedWhenTokenViewNull() {
        assertThat(checker.check(null, RoleType.SUPER_ADMIN, "metadata.browse"))
                .isEqualTo(ScopeChecker.Decision.DENIED_SCOPE);
    }

    @Test
    @DisplayName("DENIED_RBAC: 角色 null")
    void deniedWhenRoleNull() {
        TokenView view = viewWith("metadata.browse");
        assertThat(checker.check(view, null, "metadata.browse"))
                .isEqualTo(ScopeChecker.Decision.DENIED_RBAC);
    }

    @Test
    @DisplayName("WORKSPACE_OWNER 通过所有 capability")
    void workspaceOwnerCanAccessAll() {
        Set<String> allCaps = io.github.zzih.rudder.mcp.capability.CapabilityCatalog.ALL.stream()
                .map(io.github.zzih.rudder.mcp.capability.Capability::id)
                .collect(java.util.stream.Collectors.toSet());
        TokenView view = new TokenView(1L, 100L, 42L, McpTokenStatus.ACTIVE,
                LocalDateTime.now().plusDays(1), allCaps);
        for (String cap : allCaps) {
            ScopeChecker.Decision decision = checker.check(view, RoleType.WORKSPACE_OWNER, cap);
            // datasource.manage 是全局资源能力，仅 SUPER_ADMIN 允许 — RBAC 闸应拒绝 owner
            if ("datasource.manage".equals(cap)) {
                assertThat(decision).as("cap=%s", cap).isEqualTo(ScopeChecker.Decision.DENIED_RBAC);
            } else {
                assertThat(decision).as("cap=%s", cap).isEqualTo(ScopeChecker.Decision.ALLOW);
            }
        }
    }
}
