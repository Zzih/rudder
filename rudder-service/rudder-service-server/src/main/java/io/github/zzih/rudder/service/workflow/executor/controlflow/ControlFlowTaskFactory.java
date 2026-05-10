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

package io.github.zzih.rudder.service.workflow.executor.controlflow;

import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.service.workflow.controlflow.AbstractControlFlowTask;
import io.github.zzih.rudder.service.workflow.controlflow.condition.ConditionTask;
import io.github.zzih.rudder.service.workflow.controlflow.dependent.DependentTask;
import io.github.zzih.rudder.service.workflow.controlflow.subworkflow.SubWorkflowTask;
import io.github.zzih.rudder.service.workflow.controlflow.switch_.SwitchTask;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 控制流节点任务构造器。根据 {@link TaskType} 选出对应 {@link AbstractControlFlowTask} 实现并注入依赖。
 *
 * <p>Runner 在单线程主循环里同步调用，产出一次性对象（每次 processControlFlowNode 都新建）。
 */
@Component
@RequiredArgsConstructor
public class ControlFlowTaskFactory {

    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;

    public AbstractControlFlowTask create(TaskType taskType, String configJson, DagNode node,
                                          ControlFlowContext ctx) {
        return switch (taskType) {
            case CONDITION -> new ConditionTask(ctx.workflowInstanceId(), configJson, taskInstanceDao);
            case SWITCH -> new SwitchTask(node.getTaskCode(), configJson, ctx.varPoolSnapshot());
            case DEPENDENT -> new DependentTask(
                    configJson, taskInstanceDao, workflowDefinitionDao, workflowInstanceDao);
            case SUB_WORKFLOW -> new SubWorkflowTask(
                    configJson, ctx.ancestorWorkflowDefinitionCodes(), ctx.varPoolSnapshot(),
                    ctx.subWorkflowExecutor());
            default -> throw new IllegalStateException("Unknown control flow type: " + taskType);
        };
    }
}
