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

package io.github.zzih.rudder.service.workflow;

import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.InstanceNameUtils;
import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.TriggerType;
import io.github.zzih.rudder.service.script.dto.TaskInstanceDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowInstanceDTO;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInstanceService {

    private final WorkflowInstanceDao workflowInstanceDao;
    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;

    /**
     * 创建工作流实例。{@code runtimeParams} 持久化为 {@code List<Property>} JSON,
     * 与 project / global 一致,VarPoolManager 在 init 时按 Property 列表语义消费。
     */
    @Transactional
    public WorkflowInstance createInstance(Long workflowDefinitionCode, TriggerType triggerType,
                                           List<Property> runtimeParams) {
        log.info("创建工作流实例, workflowDefinitionCode={}, triggerType={}", workflowDefinitionCode, triggerType);
        WorkflowDefinition workflow = workflowDefinitionDao.selectByCode(workflowDefinitionCode);
        if (workflow == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
        }

        if (workflow.getDagJson() == null || workflow.getDagJson().isBlank()) {
            throw new BizException(WorkflowErrorCode.WF_DAG_EMPTY);
        }

        LocalDateTime now = LocalDateTime.now();
        String instanceName = InstanceNameUtils.snapshotName(workflow.getName(), now);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkspaceId(workflow.getWorkspaceId());
        instance.setProjectCode(workflow.getProjectCode());
        instance.setWorkflowDefinitionCode(workflow.getCode());
        instance.setName(instanceName);
        instance.setVersionId(workflow.getPublishedVersionId());
        instance.setTriggerType(triggerType);
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setDagSnapshot(workflow.getDagJson());
        instance.setRuntimeParams(
                runtimeParams != null && !runtimeParams.isEmpty() ? JsonUtils.toJson(runtimeParams) : null);
        instance.setStartedAt(now);
        workflowInstanceDao.insert(instance);
        log.info("工作流实例创建成功, instanceId={}, workflowDefinitionCode={}, name={}", instance.getId(),
                workflowDefinitionCode, instanceName);

        return instance;
    }

    public WorkflowInstance getById(Long id) {
        WorkflowInstance instance = workflowInstanceDao.selectById(id);
        if (instance == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    public WorkflowInstance getById(Long workspaceId, Long workflowDefinitionCode, Long id) {
        WorkflowInstance instance = workflowInstanceDao.selectByWorkspaceIdAndWorkflowDefinitionCodeAndId(workspaceId,
                workflowDefinitionCode, id);
        if (instance == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    public List<WorkflowInstance> listByWorkflowDefinitionCode(Long workflowDefinitionCode) {
        return workflowInstanceDao.selectByWorkflowDefinitionCodeOrderByCreatedAtDesc(workflowDefinitionCode);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkflowInstance> pageByWorkflowDefinitionCode(Long workflowDefinitionCode,
                                                                                                       String searchVal,
                                                                                                       int pageNum,
                                                                                                       int pageSize) {
        return workflowInstanceDao.selectPageByWorkflowDefinitionCode(workflowDefinitionCode, searchVal, pageNum,
                pageSize);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkflowInstance> pageByProjectCode(Long projectCode,
                                                                                            String searchVal,
                                                                                            String status, int pageNum,
                                                                                            int pageSize) {
        return workflowInstanceDao.selectPageByProjectCode(projectCode, searchVal, status, pageNum, pageSize);
    }

    /**
     * 列出给定工作空间下所有工作流的实例。
     */
    public List<WorkflowInstance> listByWorkspaceId(Long workspaceId) {
        List<Long> workflowIds = workflowDefinitionDao.selectIdsByWorkspaceId(workspaceId);
        if (workflowIds.isEmpty()) {
            return List.of();
        }
        return workflowInstanceDao.selectByWorkflowDefinitionCodesOrderByCreatedAtDesc(workflowIds);
    }

    /**
     * 列出工作流实例的任务实例（节点执行记录）。
     */
    public List<TaskInstance> listNodeInstances(Long instanceId) {
        return taskInstanceDao.selectByWorkflowInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    @Transactional
    public void cancel(Long instanceId) {
        log.info("取消工作流实例, instanceId={}", instanceId);
        WorkflowInstance instance = getById(instanceId);
        if (instance.getStatus().isFinished()) {
            log.warn("Instance {} is already finished with status {}", instanceId, instance.getStatus());
            return;
        }

        instance.setStatus(InstanceStatus.CANCELLED);
        instance.setFinishedAt(LocalDateTime.now());
        workflowInstanceDao.updateById(instance);

        taskInstanceDao.cancelPendingAndRunningByInstanceId(instanceId, "Cancelled: workflow cancelled");
    }

    // ======================== DTO-returning methods for Controller layer ========================

    public WorkflowInstanceDTO getByIdDTO(Long workspaceId, Long workflowDefinitionCode, Long id) {
        return BeanConvertUtils.convert(getById(workspaceId, workflowDefinitionCode, id), WorkflowInstanceDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkflowInstanceDTO> pageByWorkflowDefinitionCodeDTO(Long workflowDefinitionCode,
                                                                                                             String searchVal,
                                                                                                             int pageNum,
                                                                                                             int pageSize) {
        return BeanConvertUtils.convertPage(workflowInstanceDao.selectPageByWorkflowDefinitionCode(
                workflowDefinitionCode, searchVal, pageNum, pageSize), WorkflowInstanceDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkflowInstanceDTO> pageByProjectCodeDTO(Long projectCode,
                                                                                                  String searchVal,
                                                                                                  String status,
                                                                                                  int pageNum,
                                                                                                  int pageSize) {
        return BeanConvertUtils.convertPage(
                workflowInstanceDao.selectPageByProjectCode(projectCode, searchVal, status, pageNum, pageSize),
                WorkflowInstanceDTO.class);
    }

    public List<WorkflowInstanceDTO> listByWorkspaceIdDTO(Long workspaceId) {
        return BeanConvertUtils.convertList(listByWorkspaceId(workspaceId), WorkflowInstanceDTO.class);
    }

    public WorkflowInstanceDTO createInstanceDTO(Long workflowDefinitionCode, TriggerType triggerType,
                                                 List<Property> runtimeParams) {
        return BeanConvertUtils.convert(createInstance(workflowDefinitionCode, triggerType, runtimeParams),
                WorkflowInstanceDTO.class);
    }

    public List<TaskInstanceDTO> listNodeInstancesDTO(Long instanceId) {
        return BeanConvertUtils.convertList(listNodeInstances(instanceId), TaskInstanceDTO.class);
    }
}
