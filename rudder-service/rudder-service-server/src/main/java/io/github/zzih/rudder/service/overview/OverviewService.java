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

package io.github.zzih.rudder.service.overview;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.overview.dto.OverviewStatsDTO;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 首页概览统计。SUPER_ADMIN 看全平台;普通用户仅统计他所属工作空间下的工作流/脚本。
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final WorkspaceDao workspaceDao;
    private final WorkspaceMemberDao workspaceMemberDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ScriptDao scriptDao;

    public OverviewStatsDTO getStats() {
        OverviewStatsDTO stats = new OverviewStatsDTO();
        if (UserContext.isSuperAdmin()) {
            stats.setWorkspaceCount(workspaceDao.countAll());
            stats.setWorkflowCount(workflowDefinitionDao.countAll());
            stats.setScriptCount(scriptDao.countAll());
            return stats;
        }

        Long userId = UserContext.requireUserId();
        List<Long> wsIds = workspaceMemberDao.selectByUserId(userId).stream()
                .map(WorkspaceMember::getWorkspaceId)
                .toList();
        if (wsIds.isEmpty()) {
            return stats;
        }
        stats.setWorkspaceCount((long) wsIds.size());
        stats.setWorkflowCount(workflowDefinitionDao.countByWorkspaceIds(wsIds));
        stats.setScriptCount(scriptDao.countByWorkspaceIds(wsIds));
        return stats;
    }
}
