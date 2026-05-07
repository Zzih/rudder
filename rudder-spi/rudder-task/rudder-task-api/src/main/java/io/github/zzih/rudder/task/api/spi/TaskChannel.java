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

package io.github.zzih.rudder.task.api.spi;

import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.Task;

public interface TaskChannel {

    /**
     * 根据给定的执行上下文创建任务实例。
     * 新的实现应直接覆写此方法。
     */
    default Task createNewTask(TaskExecutionContext ctx) {
        // 默认委托给旧方法以保持向后兼容
        return createTask(ctx);
    }

    /**
     * 返回 AbstractTask 的旧方法。
     * 新实现可忽略此方法 —— 直接覆写 {@link #createNewTask} 即可。
     *
     * @deprecated 请覆写 {@link #createNewTask(TaskExecutionContext)}。
     */
    @Deprecated
    default AbstractTask createTask(TaskExecutionContext ctx) {
        throw new UnsupportedOperationException(
                "Override createNewTask() or createTask() in " + getClass().getName());
    }

    /**
     * 将原始 JSON 参数字符串解析为强类型的参数对象。
     */
    AbstractTaskParams parseParams(String paramsJson);
}
