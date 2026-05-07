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

package io.github.zzih.rudder.task.api.task;

import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.util.Collections;
import java.util.List;

/**
 * 核心任务接口。所有任务类型均实现此接口,自包含执行 — handle() 内部完成所有逻辑,
 * 不再依赖外部 Runtime / Submitter。能力接口({@link ResultableTask} / {@link DataSourceAwareTask})
 * 仅声明 Task 需要哪些资源被注入。
 */
public interface Task {

    void init(TaskExecutionContext ctx) throws TaskException;

    void handle() throws TaskException;

    void cancel() throws TaskException;

    TaskStatus getStatus();

    /**
     * 任务出口的 varPool — **已按 {@code task_definition.output_params} 白名单过滤后**的最终列表。
     * 对齐 DolphinScheduler {@code AbstractParameters.getVarPool()} 语义:
     * <ul>
     *   <li>Shell/Python:从 stdout 解出 {@code ${setValue(k=v)}} 候选 → 按 ctx.outputParamsSpec 过滤</li>
     *   <li>SQL:从 firstRow 取列值 → 按 ctx.outputParamsSpec 过滤(LIST type 取首行值,多行 LIST
     *       聚合是 follow-up scope)</li>
     *   <li>无 OUT 声明的 prop 一律不进结果,防止上游意外污染下游</li>
     * </ul>
     * 元素必须是 {@code Direct.OUT};Worker 直接 merge 进 instance.varPool 不再二次过滤。
     */
    default List<Property> getVarPool() {
        return Collections.emptyList();
    }
}
