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

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * t_r_ai_tool_config —— 每个 tool(NATIVE / MCP / SKILL)最多一行,统一管:
 * <ul>
 *   <li>在哪些工作区可见({@code workspaceIds} JSON 数组;null=所有工作区)</li>
 *   <li>调用规则覆盖:{@code minRole} / {@code requireConfirm} / {@code readOnly}
 *       (null 表示该项走 {@code PermissionGate} 的代码默认)</li>
 *   <li>{@code enabled=false} + {@code workspaceIds} → 在这些工作区**隐藏** tool</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_tool_config")
public class AiToolConfig extends BaseEntity {

    private String toolName;

    /** JSON 数组字符串,如 {@code [1,3]};null 表示所有工作区生效。 */
    private String workspaceIds;

    /** VIEWER | DEVELOPER | WORKSPACE_OWNER | SUPER_ADMIN;null=走代码默认。 */
    private String minRole;

    /** null=走代码默认。 */
    private Boolean requireConfirm;

    /** null=走代码默认。 */
    private Boolean readOnly;

    /** false + workspace_ids 匹配 = 在这些工作区隐藏此 tool。 */
    private Boolean enabled;
}
