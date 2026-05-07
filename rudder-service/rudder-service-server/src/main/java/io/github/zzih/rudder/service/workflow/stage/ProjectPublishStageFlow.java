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
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.Project;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 项目发布 / 工作流发布共用同一阶段流转规则：
 * <ul>
 *   <li>申请人 = 项目 owner（项目创建者）→ 跳过项目级，直接 [WORKSPACE_OWNER]</li>
 *   <li>申请人 ≠ 项目 owner → [PROJECT_OWNER, WORKSPACE_OWNER]</li>
 * </ul>
 */
@RequiredArgsConstructor
abstract class AbstractPublishStageFlow implements ApprovalStageFlow {

    protected final ProjectDao projectDao;

    @Override
    public List<String> resolveStageChain(ApprovalRecord record) {
        if (record.getProjectCode() == null) {
            return List.of(ApprovalLevel.WORKSPACE_OWNER.name());
        }
        Project project = projectDao.selectByCode(record.getProjectCode());
        boolean applicantIsProjectOwner = project != null
                && project.getCreatedBy() != null
                && project.getCreatedBy().equals(record.getCreatedBy());
        return applicantIsProjectOwner
                ? List.of(ApprovalLevel.WORKSPACE_OWNER.name())
                : List.of(ApprovalLevel.PROJECT_OWNER.name(), ApprovalLevel.WORKSPACE_OWNER.name());
    }
}

@Component
class ProjectPublishStageFlow extends AbstractPublishStageFlow {

    ProjectPublishStageFlow(ProjectDao projectDao) {
        super(projectDao);
    }

    @Override
    public String resourceType() {
        return ApprovalResourceType.PROJECT_PUBLISH;
    }
}
