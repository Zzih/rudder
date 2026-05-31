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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.WorkspacePermissionDao;
import io.github.zzih.rudder.dao.entity.WorkspacePermission;
import io.github.zzih.rudder.dao.mapper.WorkspacePermissionMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WorkspacePermissionDaoImpl implements WorkspacePermissionDao {

    private final WorkspacePermissionMapper workspacePermissionMapper;

    @Override
    public List<Long> selectResourceIdsByWorkspaceAndType(Long workspaceId, String resourceType) {
        return workspacePermissionMapper.queryResourceIdsByWorkspaceAndType(workspaceId, resourceType);
    }

    @Override
    public List<Long> selectWorkspaceIdsByResource(String resourceType, Long resourceId) {
        return workspacePermissionMapper.queryWorkspaceIdsByResource(resourceType, resourceId);
    }

    @Override
    public long countByResourceAndWorkspace(String resourceType, Long resourceId, Long workspaceId) {
        return workspacePermissionMapper.countByResourceAndWorkspace(resourceType, resourceId, workspaceId);
    }

    @Override
    public int insert(WorkspacePermission permission) {
        return workspacePermissionMapper.insert(permission);
    }

    @Override
    public int deleteByResourceAndWorkspace(String resourceType, Long resourceId, Long workspaceId) {
        return workspacePermissionMapper.deleteByResourceAndWorkspace(resourceType, resourceId, workspaceId);
    }

    @Override
    public int deleteByResource(String resourceType, Long resourceId) {
        return workspacePermissionMapper.deleteByResource(resourceType, resourceId);
    }
}
