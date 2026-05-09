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

import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Map;
import java.util.Optional;

/**
 * 全局唯一 active runtime。两件事:
 * <ol>
 *   <li>{@link #envVars()} — 注入到 {@code TaskExecutionContext},Shell/Python/JAR 类任务可读</li>
 *   <li>{@link #taskFactoryFor(TaskType)} — 选择性接管某些 TaskType 的实例化;
 *       返回非空 → Worker 用云上 Task 子类(handle 走云 SDK);
 *       返回空 → Worker 走 channel 默认工厂(handle 走原生实现)</li>
 * </ol>
 *
 * <p>不再有 "executor" 概念 —— 云上任务就是原生 Task 的子类,执行入口统一为 {@code task.handle()}。
 */
public interface EngineRuntime extends AutoCloseable {

    String provider();

    /** 注入 Worker 本机子进程 environment;同名 key 覆盖父进程值。 */
    default Map<String, String> envVars() {
        return Map.of();
    }

    /** 该 TaskType 是否被本 runtime 接管(返回云上子类工厂)。empty = 走 channel 默认。 */
    Optional<TaskFactory> taskFactoryFor(TaskType type);

    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    @Override
    default void close() {
    }
}
