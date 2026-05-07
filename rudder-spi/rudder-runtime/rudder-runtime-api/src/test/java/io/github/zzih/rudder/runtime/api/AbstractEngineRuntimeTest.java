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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.Task;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AbstractEngineRuntimeTest {

    @Test
    void provider_null_throws_npe() {
        assertThrows(NullPointerException.class,
                () -> new TestRuntime(null, null, List.of()));
    }

    @Test
    void envVars_null_yields_empty_map() {
        TestRuntime r = new TestRuntime("X", null, List.of());
        assertEquals(Map.of(), r.envVars());
    }

    @Test
    void envVars_defensive_copy_is_immutable() {
        Map<String, String> source = new HashMap<>();
        source.put("FOO", "bar");
        TestRuntime r = new TestRuntime("X", source, List.of());
        source.put("MUTATED", "after"); // 改动源不应影响 runtime
        assertEquals(Map.of("FOO", "bar"), r.envVars());
        assertThrows(UnsupportedOperationException.class, () -> r.envVars().put("X", "Y"));
    }

    @Test
    void empty_bindings_return_empty_factory() {
        TestRuntime r = new TestRuntime("X", null, List.of());
        assertTrue(r.taskFactoryFor(TaskType.SHELL).isEmpty());
        assertTrue(r.taskFactoryFor(TaskType.SPARK_SQL).isEmpty());
    }

    @Test
    void registered_taskType_returns_factory() throws TaskException {
        TestRuntime r = new TestRuntime("X", null, List.of(
                AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                        (ctx, p) -> new StubTask(p))));
        Optional<TaskFactory> f = r.taskFactoryFor(TaskType.SHELL);
        assertTrue(f.isPresent());
        // 校验 paramsJson 解析出的 P 被传给构造器
        Task t = f.get().create(ctxWithJson("{\"name\":\"hello\",\"count\":7}"));
        assertEquals("hello", ((StubTask) t).params.name);
        assertEquals(7, ((StubTask) t).params.count);
    }

    @Test
    void duplicate_taskType_binding_throws_with_provider_context() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TestRuntime("MYPROV", null, List.of(
                        AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                                (ctx, p) -> new StubTask(p)),
                        AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                                (ctx, p) -> new StubTask(p)))));
        assertTrue(ex.getMessage().contains("SHELL"), ex.getMessage());
        assertTrue(ex.getMessage().contains("MYPROV"), ex.getMessage());
    }

    @Test
    void bind_null_args_throw_npe() {
        assertThrows(NullPointerException.class, () -> AbstractEngineRuntime.bind(
                null, TestParams.class, (ctx, p) -> new StubTask(p)));
        assertThrows(NullPointerException.class, () -> AbstractEngineRuntime.<TestParams>bind(
                TaskType.SHELL, null, (ctx, p) -> new StubTask(p)));
        assertThrows(NullPointerException.class, () -> AbstractEngineRuntime.bind(
                TaskType.SHELL, TestParams.class, null));
    }

    @Test
    void paramsJson_null_throws_with_taskType_and_provider() {
        TestRuntime r = new TestRuntime("MYPROV", null, List.of(
                AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                        (ctx, p) -> new StubTask(p))));
        TaskFactory f = r.taskFactoryFor(TaskType.SHELL).orElseThrow();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> f.create(ctxWithJson(null)));
        assertTrue(ex.getMessage().contains("SHELL"), ex.getMessage());
        assertTrue(ex.getMessage().contains("MYPROV"), ex.getMessage());
    }

    @Test
    void paramsJson_blank_throws() {
        TestRuntime r = new TestRuntime("MYPROV", null, List.of(
                AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                        (ctx, p) -> new StubTask(p))));
        TaskFactory f = r.taskFactoryFor(TaskType.SHELL).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> f.create(ctxWithJson("")));
        assertThrows(IllegalArgumentException.class, () -> f.create(ctxWithJson("   ")));
    }

    @Test
    void healthCheck_default_is_healthy() {
        TestRuntime r = new TestRuntime("X", null, List.of());
        assertEquals(HealthStatus.healthy(), r.healthCheck());
    }

    @Test
    void provider_returns_set_value() {
        TestRuntime r = new TestRuntime("MYPROV", null, List.of());
        assertEquals("MYPROV", r.provider());
    }

    @Test
    void multiple_taskTypes_bound_independently() throws TaskException {
        TestRuntime r = new TestRuntime("X", null, List.of(
                AbstractEngineRuntime.bind(TaskType.SHELL, TestParams.class,
                        (ctx, p) -> new StubTask(p)),
                AbstractEngineRuntime.bind(TaskType.PYTHON, TestParams.class,
                        (ctx, p) -> new StubTask(p))));
        assertTrue(r.taskFactoryFor(TaskType.SHELL).isPresent());
        assertTrue(r.taskFactoryFor(TaskType.PYTHON).isPresent());
        assertFalse(r.taskFactoryFor(TaskType.HTTP).isPresent());
        // 两个 binding 拿到的 factory 不应是同一个对象
        assertSame(r.taskFactoryFor(TaskType.SHELL).get(),
                r.taskFactoryFor(TaskType.SHELL).get(), "应缓存同一个 factory");
    }

    private static TaskExecutionContext ctxWithJson(String json) {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setParamsJson(json);
        return ctx;
    }

    /** 测试用最小子类。 */
    private static final class TestRuntime extends AbstractEngineRuntime {

        TestRuntime(String provider, Map<String, String> env, List<Binding<?>> bindings) {
            super(provider, env, bindings);
        }
    }

    /** Jackson 可反序列化的简单 POJO。 */
    public static final class TestParams {

        public String name;
        public int count;
    }

    /** Task 接口的最小 stub,持有 params 让测试断言反序列化结果。 */
    private static final class StubTask implements Task {

        final TestParams params;

        StubTask(TestParams p) {
            this.params = p;
        }

        @Override
        public void init(TaskExecutionContext ctx) {
        }

        @Override
        public void handle() {
        }

        @Override
        public void cancel() {
        }

        @Override
        public TaskStatus getStatus() {
            return TaskStatus.SUBMITTED;
        }
    }
}
