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

import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.service.config.LogStorageService;
import io.github.zzih.rudder.service.script.TaskDispatchService;
import io.github.zzih.rudder.service.workflow.controlflow.subworkflow.SubWorkflowExecutor;
import io.github.zzih.rudder.service.workflow.executor.controlflow.ControlFlowTaskFactory;
import io.github.zzih.rudder.service.workflow.executor.dag.ResumeStateReconciler;

import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link WorkflowInstanceRunner} 的 Spring 级依赖聚合。
 *
 * <p>拆 record 是为了把 Runner 构造函数从 15+ 参数降到合理数量（
 * 聚合 11 个 Spring bean + 2 个工厂到此，Runner 构造只接 4 个实例级字段）。
 *
 * <p>由 {@link WorkflowExecutor} 从容器一次性组装后传给每个 Runner 实例；
 * Runner 持有引用但不做任何 Spring 生命周期管理。
 */
public record RunnerDependencies(
        TaskDispatchService taskDispatchService,
        TaskDefinitionDao taskDefinitionDao,
        TaskInstanceDao taskInstanceDao,
        WorkflowDefinitionDao workflowDefinitionDao,
        ProjectDao projectDao,
        WorkflowInstanceDao workflowInstanceDao,
        SubWorkflowExecutor subWorkflowExecutor,
        LogStorageService logService,
        TransactionTemplate txTemplate,
        TaskInstanceFactory taskInstanceFactory,
        ControlFlowTaskFactory controlFlowTaskFactory,
        ResumeStateReconciler resumeStateReconciler) {
}
