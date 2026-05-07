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
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.User;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN 阶段：候选 = 全平台所有 SUPER_ADMIN，去除申请人本人。
 *
 * <p>用于 MCP 高敏 capability（如 {@code workflow.publish} / {@code datasource.manage}）
 * 的二级审批 — workspace owner 通过后还需 SUPER_ADMIN 复核。
 */
@Component
@RequiredArgsConstructor
class SuperAdminResolver implements ApproverResolver {

    private final UserDao userDao;

    @Override
    public String stage() {
        return ApprovalLevel.SUPER_ADMIN.name();
    }

    @Override
    public List<Long> resolveCandidateUserIds(ApprovalRecord record) {
        return userDao.selectSuperAdmins().stream()
                .map(User::getId)
                .filter(uid -> !Objects.equals(uid, record.getCreatedBy()))
                .toList();
    }
}
