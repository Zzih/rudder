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

import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.mapper.WorkflowDefinitionMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WorkflowDefinitionDaoImpl implements WorkflowDefinitionDao {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    @Override
    public WorkflowDefinition selectById(Long id) {
        return workflowDefinitionMapper.selectById(id);
    }

    @Override
    public WorkflowDefinition selectByCode(Long code) {
        return workflowDefinitionMapper.queryByCode(code);
    }

    @Override
    public WorkflowDefinition selectByWorkspaceIdAndProjectCodeAndCode(Long workspaceId, Long projectCode, Long code) {
        return workflowDefinitionMapper.queryByWorkspaceIdAndProjectCodeAndCode(workspaceId, projectCode, code);
    }

    @Override
    public List<WorkflowDefinition> selectByProjectCodeOrderByUpdatedAtDesc(Long projectCode) {
        return workflowDefinitionMapper.queryByProjectCode(projectCode);
    }

    @Override
    public IPage<WorkflowDefinition> selectPageByProjectCode(Long projectCode, String searchVal, int pageNum,
                                                             int pageSize) {
        return workflowDefinitionMapper.queryPageByProjectCode(new Page<>(pageNum, pageSize), projectCode, searchVal);
    }

    @Override
    public List<WorkflowDefinition> selectByWorkspaceIdOrderByUpdatedAtDesc(Long workspaceId) {
        return workflowDefinitionMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public List<Long> selectIdsByWorkspaceId(Long workspaceId) {
        return workflowDefinitionMapper.queryIdsByWorkspaceId(workspaceId);
    }

    @Override
    public long countByProjectCodeAndName(Long projectCode, String name) {
        return workflowDefinitionMapper.countByProjectCodeAndName(projectCode, name);
    }

    @Override
    public long countAll() {
        return workflowDefinitionMapper.countAll();
    }

    @Override
    public long countByWorkspaceIds(Collection<Long> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0L;
        }
        return workflowDefinitionMapper.countByWorkspaceIds(workspaceIds);
    }

    @Override
    public WorkflowDefinition selectForUpdateByCode(Long code) {
        return workflowDefinitionMapper.selectForUpdateByCode(code);
    }

    @Override
    public int insert(WorkflowDefinition workflow) {
        return workflowDefinitionMapper.insert(workflow);
    }

    @Override
    public int updateById(WorkflowDefinition workflow) {
        return workflowDefinitionMapper.updateById(workflow);
    }

    @Override
    public int deleteById(Long id) {
        return workflowDefinitionMapper.deleteById(id);
    }
}
