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

package io.github.zzih.rudder.service.permission;

import io.github.zzih.rudder.common.enums.workspace.WorkspaceResourceType;
import io.github.zzih.rudder.dao.dao.WorkspacePermissionDao;
import io.github.zzih.rudder.dao.entity.WorkspacePermission;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局资源(数据源 / 权限包等)按工作空间授权可见性的统一管理。resourceType 区分资源种类,
 * 一份 dao / service 服务所有按工作空间授权的资源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspacePermissionService {

    private final WorkspacePermissionDao workspacePermissionDao;

    private void insert(WorkspaceResourceType type, Long resourceId, Long workspaceId, Long operatorId) {
        WorkspacePermission permission = new WorkspacePermission();
        permission.setResourceType(type.name());
        permission.setResourceId(resourceId);
        permission.setWorkspaceId(workspaceId);
        permission.setCreatedBy(operatorId);
        permission.setCreatedAt(LocalDateTime.now());
        workspacePermissionDao.insert(permission);
        log.info("Workspace permission granted: type={}, resourceId={}, workspaceId={}, by={}",
                type, resourceId, workspaceId, operatorId);
    }

    /** 撤销工作空间对资源的访问权限。 */
    public void revoke(WorkspaceResourceType type, Long resourceId, Long workspaceId) {
        workspacePermissionDao.deleteByResourceAndWorkspace(type.name(), resourceId, workspaceId);
    }

    /** 资源删除时清空其全部授权(级联)。 */
    public void revokeAll(WorkspaceResourceType type, Long resourceId) {
        workspacePermissionDao.deleteByResource(type.name(), resourceId);
    }

    public boolean hasPermission(WorkspaceResourceType type, Long resourceId, Long workspaceId) {
        return workspacePermissionDao.countByResourceAndWorkspace(type.name(), resourceId, workspaceId) > 0;
    }

    /** 列出资源已授权的工作空间 id。 */
    public List<Long> listGrantedWorkspaceIds(WorkspaceResourceType type, Long resourceId) {
        return workspacePermissionDao.selectWorkspaceIdsByResource(type.name(), resourceId);
    }

    /** 列出某工作空间在该资源类型下可见的资源 id。 */
    public List<Long> listResourceIdsByWorkspace(WorkspaceResourceType type, Long workspaceId) {
        return workspacePermissionDao.selectResourceIdsByWorkspaceAndType(workspaceId, type.name());
    }

    /**
     * 用 workspaceIds 全量覆盖资源的可见工作空间集合(diff:不在新集合的删,新增的插)。
     * 幂等,调用方传当前期望的完整集合即可;全程同一事务,避免半应用的可见性集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setGrants(WorkspaceResourceType type, Long resourceId, Set<Long> workspaceIds, Long operatorId) {
        Set<Long> current = new HashSet<>(workspacePermissionDao.selectWorkspaceIdsByResource(type.name(), resourceId));
        for (Long wsId : current) {
            if (!workspaceIds.contains(wsId)) {
                revoke(type, resourceId, wsId);
            }
        }
        for (Long wsId : workspaceIds) {
            // current 已证明不存在,直接插,省去 grant() 的重复 hasPermission COUNT。
            if (!current.contains(wsId)) {
                insert(type, resourceId, wsId, operatorId);
            }
        }
    }
}
