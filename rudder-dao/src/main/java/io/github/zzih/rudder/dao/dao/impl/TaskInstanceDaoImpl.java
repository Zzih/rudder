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

import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.enums.RuntimeType;
import io.github.zzih.rudder.dao.mapper.TaskInstanceMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TaskInstanceDaoImpl implements TaskInstanceDao {

    private final TaskInstanceMapper taskInstanceMapper;

    @Override
    public TaskInstance selectById(Long id) {
        return taskInstanceMapper.selectById(id);
    }

    @Override
    public List<TaskInstance> selectByScriptCodeOrderByCreatedAtDesc(Long scriptCode) {
        return taskInstanceMapper.queryByScriptCode(scriptCode);
    }

    @Override
    public List<TaskInstance> selectByWorkflowInstanceId(Long workflowInstanceId) {
        return taskInstanceMapper.queryByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public List<TaskInstance> selectByWorkflowInstanceIdOrderByCreatedAtAsc(Long workflowInstanceId) {
        return taskInstanceMapper.queryByWorkflowInstanceIdOrderByCreatedAtAsc(workflowInstanceId);
    }

    @Override
    public List<TaskInstance> selectFinishedByWorkflowInstanceId(Long workflowInstanceId) {
        return taskInstanceMapper.queryFinishedByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public int claimPending(Long taskInstanceId, RuntimeType runtimeType, LocalDateTime startedAt) {
        return taskInstanceMapper.claimPending(taskInstanceId,
                runtimeType != null ? runtimeType.name() : null, startedAt);
    }

    @Override
    public TaskInstance selectLatestByWorkflowInstanceIdAndTaskDefinitionCode(Long workflowInstanceId,
                                                                              Long taskDefinitionCode) {
        return taskInstanceMapper.queryLatestByWorkflowInstanceIdAndTaskDefinitionCode(workflowInstanceId,
                taskDefinitionCode);
    }

    @Override
    public List<TaskInstance> selectRunningByWorkflowInstanceId(Long workflowInstanceId) {
        return taskInstanceMapper.queryRunningByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public Map<Long, TaskInstance> selectLatestByWorkflowInstanceIdGroupedByTaskCode(Long workflowInstanceId) {
        List<TaskInstance> rows =
                taskInstanceMapper.queryLatestByWorkflowInstanceIdGroupedByTaskCode(workflowInstanceId);
        Map<Long, TaskInstance> byCode = new HashMap<>(rows.size());
        for (TaskInstance t : rows) {
            if (t.getTaskDefinitionCode() != null) {
                byCode.put(t.getTaskDefinitionCode(), t);
            }
        }
        return byCode;
    }

    @Override
    public int cancelPendingAndRunningByInstanceId(Long workflowInstanceId, String errorMessage) {
        return taskInstanceMapper.cancelPendingAndRunningByInstanceId(workflowInstanceId, errorMessage);
    }

    @Override
    public List<TaskInstance> selectRunningAndPending() {
        return taskInstanceMapper.queryRunningAndPending();
    }

    @Override
    public IPage<TaskInstance> selectRunningPage(Long workspaceId, String name, String taskType, String runtimeType,
                                                 int pageNum, int pageSize) {
        return taskInstanceMapper.queryRunningPage(new Page<>(pageNum, pageSize), workspaceId, name, taskType,
                runtimeType);
    }

    @Override
    public List<TaskInstance> selectRunningByScriptCode(Long scriptCode) {
        return taskInstanceMapper.queryRunningByScriptCode(scriptCode);
    }

    @Override
    public int insert(TaskInstance instance) {
        return taskInstanceMapper.insert(instance);
    }

    @Override
    public int updateById(TaskInstance instance) {
        return taskInstanceMapper.updateById(instance);
    }
}
