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

package io.github.zzih.rudder.service.approval.event;

/**
 * 审批单进入终态时发布 — 让下游能力订阅审批结果做相应动作。
 *
 * <p>典型消费者：
 * <ul>
 *   <li>MCP token 模块：监听 resourceType=MCP_TOKEN 的事件，激活/驳回对应 grant</li>
 *   <li>项目发布、工作流发布：监听各自 resourceType，触发实际发布动作</li>
 * </ul>
 *
 * <p>触发时机：本地决议 finalize / 外部回调 finalize / 申请人撤回 / 定时任务过期。
 *
 * @param approvalId    审批单主键
 * @param resourceType  资源类型（如 {@code MCP_TOKEN} / {@code PROJECT_PUBLISH}）
 * @param resourceCode  资源 ID（业务侧自定义含义）
 * @param finalStatus   终态：{@code APPROVED} / {@code REJECTED} / {@code WITHDRAWN} / {@code EXPIRED}
 * @param workspaceId   关联工作空间 ID（消费者过滤用）
 * @param deciderUserId 最终决定者 user_id（撤回/过期时为 {@code null}）
 */
public record ApprovalFinalizedEvent(
        Long approvalId,
        String resourceType,
        Long resourceCode,
        String finalStatus,
        Long workspaceId,
        Long deciderUserId) {

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public boolean isApproved() {
        return STATUS_APPROVED.equals(finalStatus);
    }

    public boolean isNegative() {
        return !isApproved();
    }
}
