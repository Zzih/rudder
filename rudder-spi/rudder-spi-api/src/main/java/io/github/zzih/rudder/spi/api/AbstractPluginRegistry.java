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

package io.github.zzih.rudder.spi.api;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 插件注册表基类。子类通过 {@link ServiceLoader} 发现 {@code META-INF/services/...} 中登记的工厂，
 * 每个工厂必须有无参构造函数，不得依赖 Spring。宿主依赖通过 {@code create(ctx, config)} 在构造实例时注入。
 *
 * <p>子类用法：
 * <pre>{@code
 * @Component
 * public class FooPluginManager extends AbstractPluginRegistry<String, FooFactory> {
 *     public FooPluginManager() { super(FooFactory.class); }
 *     @Override protected String keyOf(FooFactory f) { return f.getProvider(); }
 * }
 * }</pre>
 *
 * <p>冲突处理：多个 provider 注册同一个 key 时，按 {@link PluginProviderFactory#priority()} 取胜（值大者）。
 * 平级（priority 相等）会 ERROR 级别告警，保留后发现的。
 *
 * @param <K> 工厂在注册表中的键类型（枚举、String 等）
 * @param <F> SPI 工厂接口类型，必须继承 {@link PluginProviderFactory} 以暴露 priority()
 */
@Slf4j
public abstract class AbstractPluginRegistry<K, F extends PluginProviderFactory> {

    protected final ConcurrentHashMap<K, F> factories = new ConcurrentHashMap<>();

    private final List<F> discovered;

    /**
     * 通过 {@link ServiceLoader} 从 classpath 加载所有 {@code factoryClass} 的实现。
     * 实现必须在 {@code META-INF/services/<factoryClass-binary-name>} 登记。
     */
    protected AbstractPluginRegistry(Class<F> factoryClass) {
        List<F> loaded = new ArrayList<>();
        for (F factory : ServiceLoader.load(factoryClass)) {
            loaded.add(factory);
        }
        this.discovered = List.copyOf(loaded);
    }

    @PostConstruct
    protected final void init() {
        for (F factory : discovered) {
            K key = keyOf(factory);
            F previous = factories.get(key);
            if (previous == null) {
                factories.put(key, factory);
                log.info("Registered {} plugin: {} -> {}",
                        getClass().getSimpleName(), key, factory.getClass().getSimpleName());
                continue;
            }
            int prevPriority = previous.priority();
            int currPriority = factory.priority();
            if (currPriority > prevPriority) {
                factories.put(key, factory);
                log.warn(
                        "Plugin key [{}] overridden by higher-priority factory: {} (priority={}) replaced {} (priority={})",
                        key, factory.getClass().getName(), currPriority,
                        previous.getClass().getName(), prevPriority);
            } else if (currPriority < prevPriority) {
                log.warn("Plugin key [{}] kept higher-priority factory: {} (priority={}) beats {} (priority={})",
                        key, previous.getClass().getName(), prevPriority,
                        factory.getClass().getName(), currPriority);
            } else {
                log.error(
                        "Plugin key [{}] has conflicting factories at same priority {}: keeping {}, discarding {}. "
                                + "Set distinct priority() to resolve.",
                        key, currPriority,
                        previous.getClass().getName(), factory.getClass().getName());
            }
        }
        onAfterInit();
    }

    /** 从工厂实例提取其在注册表中使用的键。 */
    protected abstract K keyOf(F factory);

    /** 初始化完成后的钩子，可用于构建派生缓存。默认空。 */
    protected void onAfterInit() {
    }
}
