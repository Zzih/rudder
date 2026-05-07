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

package io.github.zzih.rudder.execution.worker.injector;

import io.github.zzih.rudder.task.api.task.Task;

/**
 * 在任务执行前注入外部资源。
 * 根据任务能力接口类型自动匹配。
 *
 * @param <T> 该注入器处理的任务能力类型
 */
public interface ResourceInjector<T extends Task> {

    /**
     * 该注入器支持的任务能力类型。
     */
    Class<T> taskType();

    /**
     * 向任务注入资源。
     */
    void inject(T task, InjectionContext ctx);
}
