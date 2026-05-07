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

package io.github.zzih.rudder.service.workflow.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.common.enums.approval.ApprovalLevel;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class McpTokenStageFlowTest {

    private final McpTokenStageFlow flow = new McpTokenStageFlow();

    @Test
    @DisplayName("resourceType: 固定 MCP_TOKEN")
    void resourceTypeIsMcpToken() {
        assertThat(flow.resourceType()).isEqualTo(ApprovalResourceType.MCP_TOKEN);
    }

    @Test
    @DisplayName("单个 NORMAL capability → 单级 [WORKSPACE_OWNER]")
    void singleNormalCapResolvesToSingleStage() {
        ApprovalRecord r = recordWithMarker("script.author");
        assertThat(flow.resolveStageChain(r))
                .containsExactly(ApprovalLevel.WORKSPACE_OWNER.name());
    }

    @Test
    @DisplayName("单个 HIGH capability → 二级 [WORKSPACE_OWNER, SUPER_ADMIN]")
    void singleHighCapResolvesToTwoStages() {
        ApprovalRecord r = recordWithMarker("workflow.publish");
        assertThat(flow.resolveStageChain(r)).containsExactly(
                ApprovalLevel.WORKSPACE_OWNER.name(),
                ApprovalLevel.SUPER_ADMIN.name());
    }

    @Test
    @DisplayName("多个 NORMAL capability → 单级")
    void multiNormalCapsResolvesToSingleStage() {
        ApprovalRecord r = recordWithMarker("script.author,workflow.author,project.author");
        assertThat(flow.resolveStageChain(r))
                .containsExactly(ApprovalLevel.WORKSPACE_OWNER.name());
    }

    @Test
    @DisplayName("混合敏感度任意一个 HIGH → 整单走二级")
    void anyHighInListEscalatesToTwoStages() {
        ApprovalRecord r = recordWithMarker("script.author,execution.run,project.author");
        assertThat(flow.resolveStageChain(r)).containsExactly(
                ApprovalLevel.WORKSPACE_OWNER.name(),
                ApprovalLevel.SUPER_ADMIN.name());
    }

    @Test
    @DisplayName("缺 marker → 抛 IllegalStateException 而非静默降级")
    void missingMarkerThrows() {
        ApprovalRecord r = new ApprovalRecord();
        r.setDescription("Request MCP token write permission, no marker here");
        assertThatThrownBy(() -> flow.resolveStageChain(r))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing <!-- mcp-cap:");
    }

    @Test
    @DisplayName("description 含其他文本 + marker → 仍能正确解析")
    void markerEmbeddedInLargerDescriptionIsExtracted() {
        ApprovalRecord r = new ApprovalRecord();
        r.setDescription("Request write permission for MCP token \"x\":\n"
                + "  • workflow.publish — ...\n"
                + "  • script.author — ...\n"
                + "<!-- mcp-cap:workflow.publish,script.author -->");
        assertThat(flow.resolveStageChain(r)).containsExactly(
                ApprovalLevel.WORKSPACE_OWNER.name(),
                ApprovalLevel.SUPER_ADMIN.name());
    }

    private static ApprovalRecord recordWithMarker(String capList) {
        ApprovalRecord r = new ApprovalRecord();
        r.setDescription("<!-- mcp-cap:" + capList + " -->");
        return r;
    }
}
