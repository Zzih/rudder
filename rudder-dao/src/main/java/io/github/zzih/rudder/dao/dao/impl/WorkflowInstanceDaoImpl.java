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

import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.mapper.WorkflowInstanceMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WorkflowInstanceDaoImpl implements WorkflowInstanceDao {

    private final WorkflowInstanceMapper workflowInstanceMapper;

    @Override
    public WorkflowInstance selectById(Long id) {
        return workflowInstanceMapper.selectById(id);
    }

    @Override
    public WorkflowInstance selectByWorkspaceIdAndWorkflowDefinitionCodeAndId(Long workspaceId,
                                                                              Long workflowDefinitionCode, Long id) {
        return workflowInstanceMapper.queryByWorkspaceIdAndWorkflowDefinitionCodeAndId(workspaceId,
                workflowDefinitionCode, id);
    }

    @Override
    public WorkflowInstance selectLatestByWorkflowDefinitionCode(Long workflowDefinitionCode) {
        return workflowInstanceMapper.queryLatestByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public WorkflowInstance selectLatestByWorkflowDefinitionCodeInTimeRange(Long workflowDefinitionCode,
                                                                            LocalDateTime start, LocalDateTime end) {
        return workflowInstanceMapper.queryLatestByWorkflowDefinitionCodeInTimeRange(workflowDefinitionCode, start,
                end);
    }

    @Override
    public List<WorkflowInstance> selectByWorkflowDefinitionCodeOrderByCreatedAtDesc(Long workflowDefinitionCode) {
        return workflowInstanceMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public IPage<WorkflowInstance> selectPageByWorkflowDefinitionCode(Long workflowDefinitionCode, String searchVal,
                                                                      int pageNum, int pageSize) {
        return workflowInstanceMapper.queryPageByWorkflowDefinitionCode(new Page<>(pageNum, pageSize),
                workflowDefinitionCode, searchVal);
    }

    @Override
    public List<WorkflowInstance> selectByWorkflowDefinitionCodesOrderByCreatedAtDesc(List<Long> workflowDefinitionCodes) {
        return workflowInstanceMapper.queryByWorkflowDefinitionCodes(workflowDefinitionCodes);
    }

    @Override
    public List<WorkflowInstance> selectByProjectCodeOrderByCreatedAtDesc(Long projectCode) {
        return workflowInstanceMapper.queryByProjectCode(projectCode);
    }

    @Override
    public IPage<WorkflowInstance> selectPageByProjectCode(Long projectCode, String searchVal, String status,
                                                           int pageNum, int pageSize) {
        return workflowInstanceMapper.queryPageByProjectCode(new Page<>(pageNum, pageSize), projectCode, searchVal,
                status);
    }

    @Override
    public int insert(WorkflowInstance instance) {
        return workflowInstanceMapper.insert(instance);
    }

    @Override
    public int updateById(WorkflowInstance instance) {
        return workflowInstanceMapper.updateById(instance);
    }

    @Override
    public List<WorkflowInstance> selectOrphanedRunning() {
        return workflowInstanceMapper.queryOrphanedRunning();
    }

    @Override
    public List<WorkflowInstance> selectByOwnerHostAndStatus(String ownerHost, InstanceStatus status) {
        return workflowInstanceMapper.queryByOwnerHostAndStatus(ownerHost, status != null ? status.name() : null);
    }

    @Override
    public int takeOverOrphan(Long id, String oldOwner, String newOwner) {
        return workflowInstanceMapper.updateOwnerHostIfMatch(
                id, oldOwner, newOwner, InstanceStatus.RUNNING.name());
    }
}
