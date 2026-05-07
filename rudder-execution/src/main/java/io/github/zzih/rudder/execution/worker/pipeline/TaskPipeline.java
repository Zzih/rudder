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

package io.github.zzih.rudder.execution.worker.pipeline;

import io.github.zzih.rudder.execution.worker.injector.InjectionContext;
import io.github.zzih.rudder.execution.worker.injector.ResourceInjector;
import io.github.zzih.rudder.task.api.task.Task;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行前的资源注入编排:遍历所有 ResourceInjector,按能力接口匹配自动注入。
 * 出口侧的"结果收集"已经下沉到各 Task(通过注入的 ResultSink 在 handle() 内部完成),
 * Worker 只读 Task 暴露的轻量元信息(resultPath / rowCount / firstRow / appId / streaming flag)。
 */
@Slf4j
@Component
public class TaskPipeline {

    private final List<ResourceInjector<?>> injectors;

    public TaskPipeline(List<ResourceInjector<?>> injectors) {
        this.injectors = injectors;
    }

    @SuppressWarnings("unchecked")
    public void injectResources(Task task, InjectionContext ctx) {
        for (ResourceInjector<?> injector : injectors) {
            if (injector.taskType().isInstance(task)) {
                log.trace("Applying injector {} to task {}", injector.getClass().getSimpleName(),
                        task.getClass().getSimpleName());
                ((ResourceInjector<Task>) injector).inject(task, ctx);
            }
        }
    }
}
