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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.WorkflowDefinition;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {

    WorkflowDefinition queryByCode(@Param("code") Long code);

    WorkflowDefinition queryByWorkspaceIdAndProjectCodeAndCode(@Param("workspaceId") Long workspaceId,
                                                               @Param("projectCode") Long projectCode,
                                                               @Param("code") Long code);

    List<WorkflowDefinition> queryByProjectCode(@Param("projectCode") Long projectCode);

    IPage<WorkflowDefinition> queryPageByProjectCode(IPage<WorkflowDefinition> page,
                                                     @Param("projectCode") Long projectCode,
                                                     @Param("searchVal") String searchVal);

    List<WorkflowDefinition> queryByWorkspaceId(@Param("workspaceId") Long workspaceId);

    List<Long> queryIdsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    long countByProjectCodeAndName(@Param("projectCode") Long projectCode,
                                   @Param("name") String name);

    WorkflowDefinition selectForUpdateByCode(@Param("code") Long code);

    long countAll();

    long countByWorkspaceIds(@Param("workspaceIds") Collection<Long> workspaceIds);
}
