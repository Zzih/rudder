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
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

/**
 * 任务实现的基类。提供 TaskExecutionContext 管理
 * 以及 {@code init(ctx)} → {@code init()} 的桥接，使子类可以方便地覆写
 * 无参的 {@code init()}。具体任务继承此类，
 * 并额外实现能力接口（JdbcTask、ClusterTask 等）。
 */
public abstract class AbstractTask implements Task {

    protected TaskExecutionContext ctx;

    protected AbstractTask() {
    }

    protected AbstractTask(TaskExecutionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 将 {@link Task#init(TaskExecutionContext)} 桥接到无参的 {@link #init()}。
     * 需要上下文的子类可覆写此方法；其他子类覆写 {@link #init()} 即可。
     */
    @Override
    public void init(TaskExecutionContext ctx) throws TaskException {
        this.ctx = ctx;
        init();
    }

    /**
     * 供子类方便使用的无参初始化方法。由 {@link #init(TaskExecutionContext)} 调用。
     */
    public void init() throws TaskException {
        // 默认空操作，子类覆写
    }

    @Override
    public abstract void handle() throws TaskException;

    @Override
    public abstract void cancel() throws TaskException;

    @Override
    public abstract TaskStatus getStatus();
}
