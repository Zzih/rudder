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

package io.github.zzih.rudder.datasource.service;

import io.github.zzih.rudder.dao.dao.DatasourcePermissionDao;
import io.github.zzih.rudder.dao.entity.DatasourcePermission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourcePermissionService {

    private final DatasourcePermissionDao datasourcePermissionDao;

    /**
     * 授予工作空间访问数据源的权限。
     */
    public void grant(Long datasourceId, Long workspaceId, Long operatorId) {
        log.info("授予数据源权限, datasourceId={}, workspaceId={}, operatorId={}", datasourceId, workspaceId, operatorId);
        // 避免重复授权
        if (hasPermission(datasourceId, workspaceId)) {
            log.debug("数据源权限已存在, 跳过授权, datasourceId={}, workspaceId={}", datasourceId, workspaceId);
            return;
        }

        DatasourcePermission permission = new DatasourcePermission();
        permission.setDatasourceId(datasourceId);
        permission.setWorkspaceId(workspaceId);
        permission.setCreatedBy(operatorId);
        permission.setCreatedAt(LocalDateTime.now());
        datasourcePermissionDao.insert(permission);
    }

    /**
     * 撤销工作空间对数据源的访问权限。
     */
    public void revoke(Long datasourceId, Long workspaceId) {
        log.info("撤销数据源权限, datasourceId={}, workspaceId={}", datasourceId, workspaceId);
        datasourcePermissionDao.deleteByDatasourceIdAndWorkspaceId(datasourceId, workspaceId);
    }

    /**
     * 检查工作空间是否有权限访问某个数据源。
     */
    public boolean hasPermission(Long datasourceId, Long workspaceId) {
        return datasourcePermissionDao.countByDatasourceIdAndWorkspaceId(datasourceId, workspaceId) > 0;
    }

    /**
     * 列出指定数据源的所有权限记录。
     */
    public List<DatasourcePermission> listByDatasource(Long datasourceId) {
        return datasourcePermissionDao.selectByDatasourceId(datasourceId);
    }

    /**
     * 用 workspaceIds 全量覆盖数据源的可见工作空间集合(diff:不在新集合的删,新增的插)。
     * 幂等,调用方传当前期望的完整集合即可。
     */
    public void setGrants(Long datasourceId, Set<Long> workspaceIds, Long operatorId) {
        Set<Long> current = listByDatasource(datasourceId).stream()
                .map(DatasourcePermission::getWorkspaceId)
                .collect(Collectors.toSet());
        for (Long wsId : current) {
            if (!workspaceIds.contains(wsId)) {
                revoke(datasourceId, wsId);
            }
        }
        for (Long wsId : workspaceIds) {
            if (!current.contains(wsId)) {
                grant(datasourceId, wsId, operatorId);
            }
        }
    }
}
