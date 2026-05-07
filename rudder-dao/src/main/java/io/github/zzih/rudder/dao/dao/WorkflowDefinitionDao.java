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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.WorkflowDefinition;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface WorkflowDefinitionDao {

    WorkflowDefinition selectById(Long id);

    WorkflowDefinition selectByCode(Long code);

    WorkflowDefinition selectByWorkspaceIdAndProjectCodeAndCode(Long workspaceId, Long projectCode, Long code);

    List<WorkflowDefinition> selectByProjectCodeOrderByUpdatedAtDesc(Long projectCode);

    IPage<WorkflowDefinition> selectPageByProjectCode(Long projectCode, String searchVal, int pageNum, int pageSize);

    List<WorkflowDefinition> selectByWorkspaceIdOrderByUpdatedAtDesc(Long workspaceId);

    List<Long> selectIdsByWorkspaceId(Long workspaceId);

    long countByProjectCodeAndName(Long projectCode, String name);

    long countAll();

    long countByWorkspaceIds(Collection<Long> workspaceIds);

    WorkflowDefinition selectForUpdateByCode(Long code);

    int insert(WorkflowDefinition workflow);

    int updateById(WorkflowDefinition workflow);

    int deleteById(Long id);
}
