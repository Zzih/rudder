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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.Task;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 云上 Runtime 的基类。子类用 {@link #bind} 列出 "TaskType ↔ Params 类 ↔ 构造器",基类负责把
 * paramsJson 反序列化喂进去 + 按 TaskType 索引。
 *
 * <pre>
 * public AliyunRuntime(...) {
 *     super(PROVIDER_KEY, envVars, List.of(
 *             bind(SPARK_SQL, SqlTaskParams.class,      (ctx, p) -&gt; new AliyunSparkSqlTask(ctx, p, props, client)),
 *             bind(SPARK_JAR, SparkJarTaskParams.class, (ctx, p) -&gt; new AliyunSparkJarTask(ctx, p, props, client))));
 * }
 * </pre>
 */
public abstract class AbstractEngineRuntime implements EngineRuntime {

    private final String provider;
    private final Map<String, String> envVars;
    private final Map<TaskType, TaskFactory> factories;

    protected AbstractEngineRuntime(String provider,
                                    Map<String, String> envVars,
                                    List<Binding<?>> bindings) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.envVars = envVars != null ? Map.copyOf(envVars) : Map.of();
        if (bindings == null || bindings.isEmpty()) {
            this.factories = Map.of();
            return;
        }
        EnumMap<TaskType, TaskFactory> map = new EnumMap<>(TaskType.class);
        for (Binding<?> b : bindings) {
            if (map.put(b.type(), b.toTaskFactory(provider)) != null) {
                throw new IllegalStateException(
                        "Duplicate TaskFactory for " + b.type() + " in provider " + provider);
            }
        }
        this.factories = Collections.unmodifiableMap(map);
    }

    @Override
    public final String provider() {
        return provider;
    }

    @Override
    public final Map<String, String> envVars() {
        return envVars;
    }

    @Override
    public final Optional<TaskFactory> taskFactoryFor(TaskType type) {
        return Optional.ofNullable(factories.get(type));
    }

    /** 云上 Runtime 走到这里说明 SDK client 已构造完成,默认 healthy;具体 provider 要细化时再 override。 */
    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.healthy();
    }

    /** 子类用这个静态方法在构造里声明每个 TaskType 接管的 Task 子类。 */
    protected static <P> Binding<P> bind(TaskType type,
                                         Class<P> paramsClass,
                                         BiFunction<TaskExecutionContext, P, Task> factory) {
        return new Binding<>(
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(paramsClass, "paramsClass"),
                Objects.requireNonNull(factory, "factory"));
    }

    /** 一条 "TaskType ↔ Params 类 ↔ 构造器" 绑定。{@code paramsClass} 用来反序列化 ctx.paramsJson。 */
    public record Binding<P>(
            TaskType type,
            Class<P> paramsClass,
            BiFunction<TaskExecutionContext, P, Task> factory) {

        TaskFactory toTaskFactory(String provider) {
            return ctx -> {
                String json = ctx.getParamsJson();
                if (json == null || json.isBlank()) {
                    throw new IllegalArgumentException(
                            "paramsJson is missing for TaskType " + type + " in provider " + provider);
                }
                return factory.apply(ctx, JsonUtils.fromJson(json, paramsClass));
            };
        }
    }
}
