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

package io.github.zzih.rudder.common.enums.mcp;

/** MCP token scope 授权状态机。 */
public enum McpScopeGrantStatus {

    /** 仅 WRITE scope：等待 workspace owner 审批。 */
    PENDING_APPROVAL,
    /** 已激活，可使用。 */
    ACTIVE,
    /** WRITE scope 被审批人拒绝（不可恢复，需新建 token 重申）。 */
    REJECTED,
    /** 申请人主动撤回（提交后未决议前撤销）。 */
    WITHDRAWN,
    /** 角色降级 / token 撤销 / 用户主动撤销。 */
    REVOKED
}
