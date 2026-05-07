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

package io.github.zzih.rudder.service.workflow.executor.dag;

import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.service.workflow.executor.dag.DagState.NodeState;
import io.github.zzih.rudder.service.workflow.executor.event.CompletionEventRouter;
import io.github.zzih.rudder.service.workflow.executor.varpool.VarPoolManager;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HA 接管时，从 DB 现有 task_instance 行 + 持久化 varPool 反推 {@link DagState} / {@link VarPoolManager}
 * 到"与旧 Server 死掉前等价"的状态。
 *
 * <p>已知限制：若被接管的 wf 有 SUB_WORKFLOW 节点且对应 task_instance=RUNNING，
 * 原 Server 的 {@code awaitCompletion} 线程已死，不再写回父节点的 control-flow task_instance；
 * 此情形下恢复出的 runner 会永久等该节点——需要后续迭代补充"控制流孤儿"子协调逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeStateReconciler {

    private final TaskInstanceDao taskInstanceDao;

    public void reconcile(WorkflowInstance instance, DagState dagState, VarPoolManager varPool,
                          CompletionEventRouter eventRouter) {
        varPool.restore(instance.getVarPool());

        List<TaskInstance> existing =
                taskInstanceDao.selectByWorkflowInstanceIdOrderByCreatedAtAsc(instance.getId());
        for (TaskInstance task : existing) {
            Long taskCode = task.getTaskDefinitionCode();
            if (taskCode == null || !dagState.contains(taskCode)) {
                continue;
            }
            NodeState mapped = DagState.mapFromTaskStatus(task.getStatus());
            NodeState existingState = dagState.get(taskCode);
            if (existingState.isTerminal() && !mapped.isTerminal()) {
                continue;
            }
            dagState.set(taskCode, mapped);
            if (mapped.isTerminal() && task.getId() != null) {
                eventRouter.markNotified(task.getId());
            }
        }

        log.warn("Resumed workflow instance {}: {} nodes, states={}",
                instance.getId(), dagState.snapshot().size(), dagState.summary());
    }
}
