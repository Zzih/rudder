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

import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.mapper.TaskDefinitionMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TaskDefinitionDaoImpl implements TaskDefinitionDao {

    private final TaskDefinitionMapper taskDefinitionMapper;

    @Override
    public TaskDefinition selectById(Long id) {
        return taskDefinitionMapper.selectById(id);
    }

    @Override
    public TaskDefinition selectByCode(Long code) {
        return taskDefinitionMapper.queryByCode(code);
    }

    @Override
    public TaskDefinition selectByScriptCode(Long scriptCode) {
        return taskDefinitionMapper.queryByScriptCode(scriptCode);
    }

    @Override
    public List<TaskDefinition> selectByWorkflowDefinitionCode(Long workflowDefinitionCode) {
        return taskDefinitionMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public int insert(TaskDefinition taskDefinition) {
        return taskDefinitionMapper.insert(taskDefinition);
    }

    @Override
    public int updateById(TaskDefinition taskDefinition) {
        return taskDefinitionMapper.updateById(taskDefinition);
    }

    @Override
    public int deleteById(Long id) {
        return taskDefinitionMapper.deleteById(id);
    }

    @Override
    public int deleteByWorkflowDefinitionCode(Long workflowDefinitionCode) {
        return taskDefinitionMapper.deleteByWorkflowDefinitionCode(workflowDefinitionCode);
    }
}
