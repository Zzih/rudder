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

package io.github.zzih.rudder.service.workflow.executor;

import io.github.zzih.rudder.common.utils.io.StoragePathUtils;
import io.github.zzih.rudder.common.utils.naming.InstanceNameUtils;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.service.workflow.dag.DagNode;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 纯构造器：从 DagNode + TaskDefinition + WorkflowInstance 建出 TaskInstance 快照。
 *
 * <p>不做任何 DB 写入 — 持久化由调用方（{@link WorkflowInstanceRunner}）执行。
 * 拆出来是为了让 Runner 瘦身并让 TaskInstance 构造逻辑可单测。
 */
@Component
@RequiredArgsConstructor
public class TaskInstanceFactory {

    static final String UPSTREAM_FAILED_MESSAGE = "Upstream task failed";

    private final ScriptDao scriptDao;

    public TaskInstance buildForNode(DagNode node, TaskDefinition taskDef,
                                     InstanceStatus status, WorkflowInstance instance) {
        return buildForNode(node, taskDef, status, instance, LocalDateTime.now());
    }

    /** 允许调用方传入 {@code now}，让 name 的时间戳与 startedAt/finishedAt 完全一致（避免跨秒漂移）。 */
    TaskInstance buildForNode(DagNode node, TaskDefinition taskDef,
                              InstanceStatus status, WorkflowInstance instance, LocalDateTime now) {
        String base = node.getLabel() != null ? node.getLabel() : taskDef.getName();
        TaskInstance task = new TaskInstance();
        task.setName(InstanceNameUtils.snapshotName(base, now));
        task.setTaskType(taskDef.getTaskType());
        task.setSourceType(SourceType.TASK);
        task.setWorkspaceId(instance.getWorkspaceId());
        task.setWorkflowInstanceId(instance.getId());
        task.setTaskDefinitionCode(node.getTaskCode());
        task.setStatus(status);

        Script script = taskDef.getScriptCode() != null
                ? scriptDao.selectByCode(taskDef.getScriptCode())
                : null;
        if (script != null) {
            task.setScriptCode(script.getCode());
            task.setContent(script.getContent());
            // 控制流节点缺 configJson 时从 Script 补齐（每次都是同一个 script，无需重复查）
            if (taskDef.getTaskType().isControlFlow()
                    && (taskDef.getConfigJson() == null || taskDef.getConfigJson().isBlank())) {
                taskDef.setConfigJson(script.getContent());
            }
        }
        return task;
    }

    /** SKIPPED 状态的快照：startedAt = finishedAt = now，无需入队派发。 */
    public TaskInstance buildSkipped(DagNode node, TaskDefinition taskDef, WorkflowInstance instance) {
        return buildTerminal(node, taskDef, instance, InstanceStatus.SKIPPED, null);
    }

    /** 上游 FAILED 级联标记的快照。 */
    public TaskInstance buildUpstreamFailed(DagNode node, TaskDefinition taskDef, WorkflowInstance instance) {
        return buildTerminal(node, taskDef, instance, InstanceStatus.FAILED, UPSTREAM_FAILED_MESSAGE);
    }

    /** logPath 计算（需要 TaskInstance.id，故在 insert 后调用）。 */
    public String logPath(TaskInstance task, WorkflowInstance instance, String workspaceName) {
        return StoragePathUtils.logPath(
                workspaceName, null, instance.getWorkflowDefinitionCode(),
                instance.getId(), task.getName(), task.getId());
    }

    /**
     * 控制流节点且 configJson 为空时，从关联 Script 回填。单独暴露给 Runner 的控制流路径使用；
     * {@link #buildForNode} 已在同一次 Script 查询中内联做了这件事，所以它不会被两次触发。
     */
    public void ensureConfigJson(TaskDefinition taskDef) {
        if (taskDef.getTaskType().isControlFlow() && taskDef.getScriptCode() != null
                && (taskDef.getConfigJson() == null || taskDef.getConfigJson().isBlank())) {
            Script script = scriptDao.selectByCode(taskDef.getScriptCode());
            if (script != null) {
                taskDef.setConfigJson(script.getContent());
            }
        }
    }

    private TaskInstance buildTerminal(DagNode node, TaskDefinition taskDef, WorkflowInstance instance,
                                       InstanceStatus status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        TaskInstance task = buildForNode(node, taskDef, status, instance, now);
        task.setStartedAt(now);
        task.setFinishedAt(now);
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }
        return task;
    }
}
