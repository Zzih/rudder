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

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.WorkflowScheduleDao;
import io.github.zzih.rudder.dao.entity.WorkflowSchedule;
import io.github.zzih.rudder.dao.enums.ScheduleStatus;
import io.github.zzih.rudder.service.workflow.dto.WorkflowScheduleDTO;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowScheduleService {

    private final WorkflowScheduleDao workflowScheduleDao;

    public WorkflowSchedule getByWorkflowDefinitionCode(Long workflowDefinitionCode) {
        return workflowScheduleDao.selectByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    public WorkflowSchedule saveOrUpdate(Long workflowDefinitionCode, WorkflowSchedule schedule) {
        log.info("保存/更新工作流调度, workflowDefinitionCode={}, cron={}", workflowDefinitionCode,
                schedule.getCronExpression());
        WorkflowSchedule existing = getByWorkflowDefinitionCode(workflowDefinitionCode);
        if (existing != null) {
            schedule.setId(existing.getId());
            schedule.setWorkflowDefinitionCode(workflowDefinitionCode);
            // 入参未指定 status 时保留旧值,避免编辑表单未带该字段意外覆盖 ONLINE → null
            if (schedule.getStatus() == null) {
                schedule.setStatus(existing.getStatus());
            }
            workflowScheduleDao.updateById(schedule);
            return schedule;
        } else {
            schedule.setWorkflowDefinitionCode(workflowDefinitionCode);
            if (schedule.getStatus() == null) {
                schedule.setStatus(ScheduleStatus.OFFLINE);
            }
            workflowScheduleDao.insert(schedule);
            return schedule;
        }
    }

    /** 切换上下线; schedule 不存在时报错(无 cron 配置无法上线)。 */
    public WorkflowSchedule toggleStatus(Long workflowDefinitionCode) {
        WorkflowSchedule existing = getByWorkflowDefinitionCode(workflowDefinitionCode);
        if (existing == null) {
            throw new IllegalStateException("schedule not configured, cannot toggle");
        }
        existing.setStatus(existing.getStatus() == ScheduleStatus.ONLINE
                ? ScheduleStatus.OFFLINE
                : ScheduleStatus.ONLINE);
        workflowScheduleDao.updateById(existing);
        log.info("切换调度状态, workflowDefinitionCode={}, status={}",
                workflowDefinitionCode, existing.getStatus());
        return existing;
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public WorkflowScheduleDTO getByWorkflowDefinitionCodeDetail(Long workflowDefinitionCode) {
        return BeanConvertUtils.convert(getByWorkflowDefinitionCode(workflowDefinitionCode), WorkflowScheduleDTO.class);
    }

    public WorkflowScheduleDTO saveOrUpdateDetail(Long workflowDefinitionCode, WorkflowScheduleDTO body) {
        WorkflowSchedule entity = BeanConvertUtils.convert(body, WorkflowSchedule.class);
        return BeanConvertUtils.convert(saveOrUpdate(workflowDefinitionCode, entity), WorkflowScheduleDTO.class);
    }

    public WorkflowScheduleDTO toggleStatusDetail(Long workflowDefinitionCode) {
        return BeanConvertUtils.convert(toggleStatus(workflowDefinitionCode), WorkflowScheduleDTO.class);
    }
}
