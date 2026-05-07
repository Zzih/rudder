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

import io.github.zzih.rudder.dao.entity.WorkflowInstance;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {

    WorkflowInstance queryByWorkspaceIdAndWorkflowDefinitionCodeAndId(@Param("workspaceId") Long workspaceId,
                                                                      @Param("workflowDefinitionCode") Long workflowDefinitionCode,
                                                                      @Param("id") Long id);

    WorkflowInstance queryLatestByWorkflowDefinitionCode(@Param("workflowDefinitionCode") Long workflowDefinitionCode);

    WorkflowInstance queryLatestByWorkflowDefinitionCodeInTimeRange(@Param("workflowDefinitionCode") Long workflowDefinitionCode,
                                                                    @Param("start") LocalDateTime start,
                                                                    @Param("end") LocalDateTime end);

    List<WorkflowInstance> queryByWorkflowDefinitionCode(@Param("workflowDefinitionCode") Long workflowDefinitionCode);

    IPage<WorkflowInstance> queryPageByWorkflowDefinitionCode(IPage<WorkflowInstance> page,
                                                              @Param("workflowDefinitionCode") Long workflowDefinitionCode,
                                                              @Param("searchVal") String searchVal);

    List<WorkflowInstance> queryByWorkflowDefinitionCodes(@Param("workflowDefinitionCodes") List<Long> workflowDefinitionCodes);

    List<WorkflowInstance> queryByProjectCode(@Param("projectCode") Long projectCode);

    IPage<WorkflowInstance> queryPageByProjectCode(IPage<WorkflowInstance> page,
                                                   @Param("projectCode") Long projectCode,
                                                   @Param("searchVal") String searchVal,
                                                   @Param("status") String status);

    List<WorkflowInstance> queryOrphanedRunning();

    List<WorkflowInstance> queryByOwnerHostAndStatus(@Param("ownerHost") String ownerHost,
                                                     @Param("status") String status);

    int updateOwnerHostIfMatch(@Param("id") Long id,
                               @Param("oldOwner") String oldOwner,
                               @Param("newOwner") String newOwner,
                               @Param("status") String status);
}
