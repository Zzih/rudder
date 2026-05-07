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

import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.enums.RuntimeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface TaskInstanceDao {

    TaskInstance selectById(Long id);

    List<TaskInstance> selectByScriptCodeOrderByCreatedAtDesc(Long scriptCode);

    List<TaskInstance> selectByWorkflowInstanceId(Long workflowInstanceId);

    List<TaskInstance> selectByWorkflowInstanceIdOrderByCreatedAtAsc(Long workflowInstanceId);

    /**
     * 查询指定工作流实例下所有已完成状态（SUCCESS/FAILED/CANCELLED/SKIPPED）的任务，
     * 供 {@code WorkflowExecutor.pollTaskCompletions} 使用。DB 侧过滤避免全量加载。
     */
    List<TaskInstance> selectFinishedByWorkflowInstanceId(Long workflowInstanceId);

    /**
     * 接单原子性保护：当且仅当任务仍处于 PENDING 时，CAS 更新为 RUNNING。
     * 返回受影响行数，0 表示已被其他线程/重试接单（或已终止），调用方应放弃执行。
     */
    int claimPending(Long taskInstanceId, RuntimeType runtimeType, LocalDateTime startedAt);

    TaskInstance selectLatestByWorkflowInstanceIdAndTaskDefinitionCode(Long workflowInstanceId,
                                                                       Long taskDefinitionCode);

    /**
     * 工作流实例下所有 RUNNING / PENDING 的 task_instance。供 Runner 取消流程一次拉全，
     * 替代"按 taskCode 逐个 {@link #selectLatestByWorkflowInstanceIdAndTaskDefinitionCode}"的 N+1。
     */
    List<TaskInstance> selectRunningByWorkflowInstanceId(Long workflowInstanceId);

    /**
     * 工作流实例下，每个 {@code task_definition_code} 取 id 最大的 task_instance，返回 map。
     * 替代 ConditionTask 等"循环调用 {@link #selectLatestByWorkflowInstanceIdAndTaskDefinitionCode}"的 N+1。
     */
    Map<Long, TaskInstance> selectLatestByWorkflowInstanceIdGroupedByTaskCode(Long workflowInstanceId);

    /**
     * 一次性把 workflow 实例下 PENDING/RUNNING 的行 flip 成 CANCELLED。
     * {@code errorMessage} 非空时会覆写 {@code error_message} 列。
     */
    int cancelPendingAndRunningByInstanceId(Long workflowInstanceId, String errorMessage);

    List<TaskInstance> selectRunningAndPending();

    IPage<TaskInstance> selectRunningPage(Long workspaceId, String name, String taskType, String runtimeType,
                                          int pageNum, int pageSize);

    List<TaskInstance> selectRunningByScriptCode(Long scriptCode);

    int insert(TaskInstance instance);

    int updateById(TaskInstance instance);
}
