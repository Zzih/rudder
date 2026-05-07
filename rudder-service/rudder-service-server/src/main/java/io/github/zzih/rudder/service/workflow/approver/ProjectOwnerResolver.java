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
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.Project;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** PROJECT_OWNER 阶段：候选 = 项目创建者（不去申请人——若申请人是项目 owner 则该阶段已被 StageFlow 跳过）。 */
@Component
@RequiredArgsConstructor
class ProjectOwnerResolver implements ApproverResolver {

    private final ProjectDao projectDao;

    @Override
    public String stage() {
        return ApprovalLevel.PROJECT_OWNER.name();
    }

    @Override
    public List<Long> resolveCandidateUserIds(ApprovalRecord record) {
        if (record.getProjectCode() == null) {
            return List.of();
        }
        Project p = projectDao.selectByCode(record.getProjectCode());
        if (p == null || p.getCreatedBy() == null) {
            return List.of();
        }
        return List.of(p.getCreatedBy());
    }
}
