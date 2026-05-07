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

import io.github.zzih.rudder.dao.entity.TaskInstance;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstance> {

    List<TaskInstance> queryByScriptCode(@Param("scriptCode") Long scriptCode);

    List<TaskInstance> queryByWorkflowInstanceId(@Param("workflowInstanceId") Long workflowInstanceId);

    List<TaskInstance> queryByWorkflowInstanceIdOrderByCreatedAtAsc(@Param("workflowInstanceId") Long workflowInstanceId);

    TaskInstance queryLatestByWorkflowInstanceIdAndTaskDefinitionCode(@Param("workflowInstanceId") Long workflowInstanceId,
                                                                      @Param("taskDefinitionCode") Long taskDefinitionCode);

    int cancelPendingAndRunningByInstanceId(@Param("workflowInstanceId") Long workflowInstanceId,
                                            @Param("errorMessage") String errorMessage);

    List<TaskInstance> queryRunningAndPending();

    IPage<TaskInstance> queryRunningPage(IPage<TaskInstance> page,
                                         @Param("workspaceId") Long workspaceId,
                                         @Param("name") String name,
                                         @Param("taskType") String taskType,
                                         @Param("runtimeType") String runtimeType);

    List<TaskInstance> queryRunningByScriptCode(@Param("scriptCode") Long scriptCode);

    List<TaskInstance> queryFinishedByWorkflowInstanceId(@Param("workflowInstanceId") Long workflowInstanceId);

    List<TaskInstance> queryRunningByWorkflowInstanceId(@Param("workflowInstanceId") Long workflowInstanceId);

    List<TaskInstance> queryLatestByWorkflowInstanceIdGroupedByTaskCode(@Param("workflowInstanceId") Long workflowInstanceId);

    int claimPending(@Param("taskInstanceId") Long taskInstanceId,
                     @Param("runtimeType") String runtimeType,
                     @Param("startedAt") LocalDateTime startedAt);
}
