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

package io.github.zzih.rudder.service.workspace.event;

/**
 * 工作空间成员变更事件 — 当 workspace 内某 user 的角色被新增 / 修改 / 移除时发布。
 *
 * <p>用途：让下游能力订阅角色变更，做相应失效。
 * 例如 MCP scope grant 监听到角色降级 → 立即将该 user 在该 workspace 的 token 写权限撤销。
 *
 * <p>记录是 record 类型（不可变值对象），符合事件即事实的语义。
 *
 * @param workspaceId 所属工作空间
 * @param userId      变更涉及的成员 user_id
 * @param oldRole     变更前角色（{@code null} 表示新增成员）
 * @param newRole     变更后角色（{@code null} 表示移除成员）
 * @param type        变更类型：ADDED / UPDATED / REMOVED
 */
public record WorkspaceMemberChangedEvent(
        Long workspaceId,
        Long userId,
        String oldRole,
        String newRole,
        ChangeType type) {

    public enum ChangeType {
        ADDED,
        UPDATED,
        REMOVED
    }

    public static WorkspaceMemberChangedEvent added(Long workspaceId, Long userId, String role) {
        return new WorkspaceMemberChangedEvent(workspaceId, userId, null, role, ChangeType.ADDED);
    }

    public static WorkspaceMemberChangedEvent updated(Long workspaceId, Long userId, String oldRole, String newRole) {
        return new WorkspaceMemberChangedEvent(workspaceId, userId, oldRole, newRole, ChangeType.UPDATED);
    }

    public static WorkspaceMemberChangedEvent removed(Long workspaceId, Long userId, String oldRole) {
        return new WorkspaceMemberChangedEvent(workspaceId, userId, oldRole, null, ChangeType.REMOVED);
    }
}
