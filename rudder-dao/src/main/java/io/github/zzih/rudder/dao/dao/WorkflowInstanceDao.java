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

import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface WorkflowInstanceDao {

    WorkflowInstance selectById(Long id);

    WorkflowInstance selectByWorkspaceIdAndWorkflowDefinitionCodeAndId(Long workspaceId, Long workflowDefinitionCode,
                                                                       Long id);

    WorkflowInstance selectLatestByWorkflowDefinitionCode(Long workflowDefinitionCode);

    WorkflowInstance selectLatestByWorkflowDefinitionCodeInTimeRange(Long workflowDefinitionCode, LocalDateTime start,
                                                                     LocalDateTime end);

    List<WorkflowInstance> selectByWorkflowDefinitionCodeOrderByCreatedAtDesc(Long workflowDefinitionCode);

    IPage<WorkflowInstance> selectPageByWorkflowDefinitionCode(Long workflowDefinitionCode, String searchVal,
                                                               int pageNum, int pageSize);

    List<WorkflowInstance> selectByWorkflowDefinitionCodesOrderByCreatedAtDesc(List<Long> workflowDefinitionCodes);

    List<WorkflowInstance> selectByProjectCodeOrderByCreatedAtDesc(Long projectCode);

    IPage<WorkflowInstance> selectPageByProjectCode(Long projectCode, String searchVal, String status, int pageNum,
                                                    int pageSize);

    int insert(WorkflowInstance instance);

    int updateById(WorkflowInstance instance);

    /**
     * 查询孤儿工作流：status=RUNNING 但 owner_host 已不是在线 Server。
     */
    List<WorkflowInstance> selectOrphanedRunning();

    /**
     * 查询指定 Server 拥有的、指定状态的工作流。用于 Server 启动自清。
     */
    List<WorkflowInstance> selectByOwnerHostAndStatus(String ownerHost, InstanceStatus status);

    /**
     * 原子接管：仅当 {@code owner_host = oldOwner AND status = 'RUNNING'} 时把 owner_host 换成 {@code newOwner}。
     * 返回受影响行数：1 = 接管成功，0 = 已被其他 Server 抢走或状态已变（放弃）。
     */
    int takeOverOrphan(Long id, String oldOwner, String newOwner);
}
