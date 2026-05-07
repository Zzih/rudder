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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zzih.rudder.common.enums.auth.RoleType;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capability 矩阵静态校验 — 强制 catalog 完整性 + 锁定 §3.3.2 矩阵。
 * 任何漂移都会被这套断言拦下来。
 */
class CapabilityCatalogTest {

    @Test
    @DisplayName("ALL 包含 20 个 capability")
    void exactly20Capabilities() {
        assertThat(CapabilityCatalog.ALL).hasSize(20);
    }

    @Test
    @DisplayName("capability id 全局唯一")
    void capabilityIdsUnique() {
        Set<String> ids = new HashSet<>();
        for (Capability c : CapabilityCatalog.ALL) {
            assertThat(ids.add(c.id()))
                    .as("Duplicate capability id: %s", c.id())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("requireById 命中 + 未知 id 抛错")
    void requireByIdSemantics() {
        Capability c = CapabilityCatalog.requireById("metadata.browse");
        assertThat(c.id()).isEqualTo("metadata.browse");
        assertThat(c.rwClass()).isEqualTo(RwClass.READ);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> CapabilityCatalog.requireById("nonexistent.cap"));
    }

    @Test
    @DisplayName("矩阵：VIEWER 角色可申请的 capability 与 §3.3.2 一致")
    void viewerMatrix() {
        Set<String> allowed = idsFor(RoleType.VIEWER);
        assertThat(allowed).containsExactlyInAnyOrder(
                "workspace.view",
                "metadata.browse",
                "datasource.view",
                "project.browse",
                "script.browse",
                "execution.view_status",
                "execution.view_result",
                "workflow.browse",
                "approval.view",
                "knowledge.search");
    }

    @Test
    @DisplayName("矩阵：DEVELOPER 角色可申请的 capability 与 §3.3.2 一致")
    void developerMatrix() {
        Set<String> allowed = idsFor(RoleType.DEVELOPER);
        assertThat(allowed).containsExactlyInAnyOrder(
                "workspace.view",
                "metadata.browse",
                "datasource.view",
                "datasource.test",
                "project.browse",
                "project.author",
                "script.browse",
                "script.author",
                "execution.view_status",
                "execution.view_result",
                "execution.run",
                "execution.cancel",
                "workflow.browse",
                "workflow.author",
                "workflow.run",
                "approval.view",
                "knowledge.search");
    }

    @Test
    @DisplayName("矩阵：WORKSPACE_OWNER 可申请 19 个 capability（datasource.manage 是全局资源，仅 SUPER_ADMIN）")
    void workspaceOwnerMatrix() {
        Set<String> allowed = idsFor(RoleType.WORKSPACE_OWNER);
        assertThat(allowed).hasSize(19);
        assertThat(allowed).contains(
                "workflow.publish",
                "approval.act",
                "knowledge.search");
        assertThat(allowed).doesNotContain("datasource.manage");
    }

    @Test
    @DisplayName("矩阵：SUPER_ADMIN 角色可申请所有 20 个 capability")
    void superAdminMatrix() {
        Set<String> allowed = idsFor(RoleType.SUPER_ADMIN);
        assertThat(allowed).hasSize(20);
    }

    @Test
    @DisplayName("R/W 类别比例：11 READ + 9 WRITE")
    void readWriteSplit() {
        long readCount = CapabilityCatalog.ALL.stream()
                .filter(c -> c.rwClass() == RwClass.READ).count();
        long writeCount = CapabilityCatalog.ALL.stream()
                .filter(c -> c.rwClass() == RwClass.WRITE).count();
        assertThat(readCount).isEqualTo(11);
        assertThat(writeCount).isEqualTo(9);
    }

    @Test
    @DisplayName("availableFor(null) 返回空列表")
    void availableForNullSafe() {
        assertThat(CapabilityCatalog.availableFor(null)).isEmpty();
    }

    @Test
    @DisplayName("Capability 构造校验：缺字段抛 IllegalArgumentException")
    void capabilityConstructValidation() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Capability("", "x", RwClass.READ, Capability.Sensitivity.NORMAL,
                        "d", Set.of(RoleType.VIEWER)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Capability("a.b", "x", RwClass.READ, Capability.Sensitivity.NORMAL,
                        "d", Set.<RoleType>of()));
    }

    @Test
    @DisplayName("敏感度：workflow.publish / datasource.manage / execution.run 标记为 HIGH")
    void highSensitivityCapabilities() {
        assertThat(CapabilityCatalog.requireById("workflow.publish").sensitivity())
                .isEqualTo(Capability.Sensitivity.HIGH);
        assertThat(CapabilityCatalog.requireById("datasource.manage").sensitivity())
                .isEqualTo(Capability.Sensitivity.HIGH);
        assertThat(CapabilityCatalog.requireById("execution.run").sensitivity())
                .isEqualTo(Capability.Sensitivity.HIGH);
        // 普通 WRITE 默认 NORMAL
        assertThat(CapabilityCatalog.requireById("script.author").sensitivity())
                .isEqualTo(Capability.Sensitivity.NORMAL);
        assertThat(CapabilityCatalog.requireById("workflow.run").sensitivity())
                .isEqualTo(Capability.Sensitivity.NORMAL);
    }

    private Set<String> idsFor(RoleType role) {
        return CapabilityCatalog.availableFor(role).stream()
                .map(Capability::id)
                .collect(java.util.stream.Collectors.toSet());
    }
}
