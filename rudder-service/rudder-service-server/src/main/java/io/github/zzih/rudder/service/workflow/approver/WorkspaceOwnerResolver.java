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

package io.github.zzih.rudder.service.workflow.approver;

import io.github.zzih.rudder.common.enums.approval.ApprovalLevel;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * WORKSPACE_OWNER 阶段：候选 = 该 workspace 所有 OWNER 角色成员，去除申请人本人。
 * 等待期间 owner 增减自动反映。
 */
@Component
@RequiredArgsConstructor
class WorkspaceOwnerResolver implements ApproverResolver {

    private final WorkspaceMemberDao workspaceMemberDao;

    @Override
    public String stage() {
        return ApprovalLevel.WORKSPACE_OWNER.name();
    }

    @Override
    public List<Long> resolveCandidateUserIds(ApprovalRecord record) {
        if (record.getWorkspaceId() == null) {
            return List.of();
        }
        return workspaceMemberDao
                .selectByWorkspaceIdAndRole(record.getWorkspaceId(), RoleType.WORKSPACE_OWNER.name())
                .stream()
                .map(m -> m.getUserId())
                .filter(uid -> !Objects.equals(uid, record.getCreatedBy()))
                .toList();
    }
}
