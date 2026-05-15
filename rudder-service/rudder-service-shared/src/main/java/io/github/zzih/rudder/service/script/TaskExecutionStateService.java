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

package io.github.zzih.rudder.service.script;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.placeholder.BuiltInParams;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.RuntimeType;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Worker 端任务实例状态读写。纯 DAO 组合,不依赖 dispatch / 通知 / 注册等 Server 侧服务,
 * 避免 Worker 经 TaskInstanceService 引入 TaskDispatchService → ServiceRegistryService 循环依赖。
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionStateService {

    private final TaskInstanceDao taskInstanceDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ProjectDao projectDao;

    /** 按 id 直拉,不做工作空间 / 权限校验。仅 Worker 内部用,公网入口必须走 TaskInstanceService.getById。 */
    public TaskInstance findByIdInternal(Long id) {
        return taskInstanceDao.selectById(id);
    }

    public void updateInternal(TaskInstance instance) {
        taskInstanceDao.updateById(instance);
    }

    /** Worker 启动幂等 CAS:仅 status=PENDING 才成功转 RUNNING,返回受影响行数。 */
    public int claimPending(Long id, RuntimeType runtimeType, LocalDateTime startedAt) {
        return taskInstanceDao.claimPending(id, runtimeType, startedAt);
    }

    /** Task definition timeout(分钟数),返回 null 表示未配置。 */
    public Integer findTaskDefinitionTimeoutMinutes(Long taskDefinitionCode) {
        if (taskDefinitionCode == null) {
            return null;
        }
        TaskDefinition def = taskDefinitionDao.selectByCode(taskDefinitionCode);
        return def != null ? def.getTimeout() : null;
    }

    /**
     * Task definition 上的 OUT 白名单(对齐 DS localParams Direct.OUT)。
     * Task 内部 dealOutParam 时按此过滤,未声明的 prop 不进 varPool。
     * IDE 直跑等无 task definition 的场景返回空列表。
     */
    public List<Property> findTaskDefinitionOutputParams(Long taskDefinitionCode) {
        if (taskDefinitionCode == null) {
            return List.of();
        }
        TaskDefinition def = taskDefinitionDao.selectByCode(taskDefinitionCode);
        if (def == null || def.getOutputParams() == null || def.getOutputParams().isBlank()) {
            return List.of();
        }
        return JsonUtils.toList(def.getOutputParams(), Property.class);
    }

    /**
     * 装填 {@link BuiltInParams.BuiltInContext}:Worker 在占位符解析前调用,把 system.* 内置变量
     * 注入到 paramMap 最低优先级槽位。缺失字段(IDE 直跑无 workflowInstance)按 nullable 留空。
     *
     * <p>baseTime 用 instance.startedAt(Worker 真正开跑时间),Rudder 不做 DS 那种业务时间补跑。
     *
     * @param executePath Worker 端任务工作目录(资源解析后的绝对路径),DAO 拿不到由 caller 传。
     */
    public BuiltInParams.BuiltInContext buildBuiltInContext(TaskInstance instance, String executePath) {
        BuiltInParams.BuiltInContext ctx = BuiltInParams.BuiltInContext.builder()
                .taskInstanceId(instance.getId())
                .taskDefinitionCode(instance.getTaskDefinitionCode())
                .workflowInstanceId(instance.getWorkflowInstanceId())
                .baseTime(instance.getStartedAt() != null ? instance.getStartedAt() : LocalDateTime.now())
                .executePath(executePath);

        if (instance.getTaskDefinitionCode() != null) {
            TaskDefinition def = taskDefinitionDao.selectByCode(instance.getTaskDefinitionCode());
            if (def != null) {
                ctx.taskDefinitionName(def.getName());
            }
        }

        if (instance.getWorkflowInstanceId() != null) {
            WorkflowInstance wfInstance = workflowInstanceDao.selectById(instance.getWorkflowInstanceId());
            if (wfInstance != null) {
                ctx.workflowDefinitionCode(wfInstance.getWorkflowDefinitionCode());
                ctx.projectCode(wfInstance.getProjectCode());
                if (wfInstance.getWorkflowDefinitionCode() != null) {
                    WorkflowDefinition def = workflowDefinitionDao.selectByCode(wfInstance.getWorkflowDefinitionCode());
                    if (def != null) {
                        ctx.workflowDefinitionName(def.getName());
                    }
                }
                if (wfInstance.getProjectCode() != null) {
                    Project project = projectDao.selectByCode(wfInstance.getProjectCode());
                    if (project != null) {
                        ctx.projectName(project.getName());
                    }
                }
            }
        }
        return ctx;
    }
}
