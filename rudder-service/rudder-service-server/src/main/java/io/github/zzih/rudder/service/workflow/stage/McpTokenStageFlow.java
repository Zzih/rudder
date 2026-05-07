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

import io.github.zzih.rudder.common.enums.approval.ApprovalLevel;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * MCP Token 写权限申请的阶段链解析。
 *
 * <p>规则（按 capability 最高敏感度路由）：
 * <ul>
 *   <li>任意 capability 是高敏（{@code workflow.publish} / {@code datasource.manage} / {@code execution.run}）：
 *       {@code [WORKSPACE_OWNER, SUPER_ADMIN]} —— workspace owner 通过后再走平台管理员复核</li>
 *   <li>否则：{@code [WORKSPACE_OWNER]} —— 单级</li>
 * </ul>
 *
 * <p>capability 列表来源：{@code McpTokenService.submitWriteApproval} 在 description 末尾埋
 * {@code <!-- mcp-cap:id1,id2,... --> } 标签，本类用正则提取后按最高敏感度决策。
 */
@Component
class McpTokenStageFlow implements ApprovalStageFlow {

    private static final Pattern CAP_TAG = Pattern.compile("<!--\\s*mcp-cap:(\\S+)\\s*-->");

    /** capability id 模式 = HIGH 敏感度（与 {@code CapabilityCatalog} 中 Sensitivity.HIGH 项一致）。 */
    private static final List<String> HIGH_SENSITIVITY_CAPS = List.of(
            "workflow.publish",
            "datasource.manage",
            "execution.run");

    @Override
    public String resourceType() {
        return ApprovalResourceType.MCP_TOKEN;
    }

    @Override
    public List<String> resolveStageChain(ApprovalRecord record) {
        List<String> capIds = parseCapIds(record.getDescription());
        boolean anyHigh = capIds.stream().anyMatch(HIGH_SENSITIVITY_CAPS::contains);
        if (anyHigh) {
            return List.of(
                    ApprovalLevel.WORKSPACE_OWNER.name(),
                    ApprovalLevel.SUPER_ADMIN.name());
        }
        return List.of(ApprovalLevel.WORKSPACE_OWNER.name());
    }

    private static List<String> parseCapIds(String description) {
        Matcher m = CAP_TAG.matcher(description);
        if (!m.find()) {
            throw new IllegalStateException(
                    "MCP_TOKEN approval missing <!-- mcp-cap:... --> marker; description=" + description);
        }
        return List.of(m.group(1).split(","));
    }
}
