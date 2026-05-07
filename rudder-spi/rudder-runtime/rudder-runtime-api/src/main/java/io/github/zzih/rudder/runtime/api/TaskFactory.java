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

package io.github.zzih.rudder.runtime.api;

import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.Task;

/**
 * 云上 Task 实例工厂。每个云 Runtime 为它接管的 TaskType 注册一个 TaskFactory,
 * Worker 派发时若拿到 factory 就用它造任务,否则走 channel 默认工厂。
 *
 * <p>云上 Task 是原生 Task 的子类(如 {@code AliyunSparkSqlTask extends SparkSqlTask}),
 * override {@code handle()} / {@code cancel()} / {@code init()} 走云端 SDK,
 * 其它(ResultSink / status / params)全部从父类继承,没有第二条执行路径。
 */
@FunctionalInterface
public interface TaskFactory {

    Task create(TaskExecutionContext ctx) throws TaskException;
}
