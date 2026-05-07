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

import io.github.zzih.rudder.dao.dao.DatasourcePermissionDao;
import io.github.zzih.rudder.dao.entity.DatasourcePermission;
import io.github.zzih.rudder.dao.mapper.DatasourcePermissionMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DatasourcePermissionDaoImpl implements DatasourcePermissionDao {

    private final DatasourcePermissionMapper datasourcePermissionMapper;

    @Override
    public List<DatasourcePermission> selectByWorkspaceId(Long workspaceId) {
        return datasourcePermissionMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public List<DatasourcePermission> selectByDatasourceId(Long datasourceId) {
        return datasourcePermissionMapper.queryByDatasourceId(datasourceId);
    }

    @Override
    public long countByDatasourceIdAndWorkspaceId(Long datasourceId, Long workspaceId) {
        return datasourcePermissionMapper.countByDatasourceIdAndWorkspaceId(datasourceId, workspaceId);
    }

    @Override
    public int insert(DatasourcePermission permission) {
        return datasourcePermissionMapper.insert(permission);
    }

    @Override
    public int deleteByDatasourceIdAndWorkspaceId(Long datasourceId, Long workspaceId) {
        return datasourcePermissionMapper.deleteByDatasourceIdAndWorkspaceId(datasourceId, workspaceId);
    }
}
