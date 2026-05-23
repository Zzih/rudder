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

package io.github.zzih.rudder.datasource.api;

import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DatasourceTypeProvider} 的静态注册中心。参考 DolphinScheduler 的
 * {@code DataSourceClientProvider} 模式 —— 类首次加载时通过 {@link ServiceLoader} 一次性发现
 * 所有 {@code META-INF/services/io.github.zzih.rudder.datasource.api.DatasourceTypeProvider}
 * 自注册的 provider(由 {@code @AutoService} 在编译期生成),无需 Spring 容器介入。
 *
 * <p>返回类型为通配符 {@code DatasourceTypeProvider<?>} —— 调用方通过 {@code propertiesClass()}
 * 拿到具体 Class,完成 JSON 反序列化与字段校验。
 */
public final class DatasourceTypeProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DatasourceTypeProviderRegistry.class);

    /** 不可变快照,业务侧只读。读路径无锁,写路径走 register 同步段。 */
    @SuppressWarnings("rawtypes")
    private static volatile Map<DatasourceType, DatasourceTypeProvider> providers = loadFromServiceLoader();

    private DatasourceTypeProviderRegistry() {
    }

    /**
     * 通过 {@link ServiceLoader} 一次性加载所有 provider。同 type 重复抛错暴露配置冲突。
     */
    @SuppressWarnings("rawtypes")
    private static Map<DatasourceType, DatasourceTypeProvider> loadFromServiceLoader() {
        Map<DatasourceType, DatasourceTypeProvider> map = new EnumMap<>(DatasourceType.class);
        for (DatasourceTypeProvider provider : ServiceLoader.load(DatasourceTypeProvider.class)) {
            DatasourceTypeProvider previous = map.put(provider.dbType(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate DatasourceTypeProvider for type " + provider.dbType()
                                + ": " + previous.getClass().getName()
                                + " vs " + provider.getClass().getName());
            }
        }
        for (DatasourceType type : DatasourceType.values()) {
            if (!map.containsKey(type)) {
                log.warn("No DatasourceTypeProvider on classpath for type {}, calls will fail at runtime", type);
            }
        }
        log.info("DatasourceTypeProviderRegistry loaded {} providers via ServiceLoader: {}",
                map.size(), map.keySet());
        return Collections.unmodifiableMap(map);
    }

    /**
     * 注册一个 provider。生产路径由 {@link ServiceLoader} 自动调用;测试可手动注入桩。
     * 同 type 重复注册同一实例幂等,不同实例抛 {@link IllegalStateException}。
     */
    @SuppressWarnings("rawtypes")
    public static synchronized void register(DatasourceTypeProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        DatasourceTypeProvider existing = providers.get(provider.dbType());
        if (existing != null && existing != provider) {
            throw new IllegalStateException(
                    "Duplicate DatasourceTypeProvider for type " + provider.dbType()
                            + ": " + existing.getClass().getName()
                            + " vs " + provider.getClass().getName());
        }
        // EnumMap 的 copy-from-Map 构造器在源 Map 为空时会抛错(无从推断 enum class),
        // 这里显式按 DatasourceType.class 起新表再 putAll,避免首次注册的 NPE。
        Map<DatasourceType, DatasourceTypeProvider> copy = new EnumMap<>(DatasourceType.class);
        copy.putAll(providers);
        copy.put(provider.dbType(), provider);
        providers = Collections.unmodifiableMap(copy);
    }

    /**
     * 按类型取 provider。未注册抛 {@link IllegalStateException},调用方应在 enum 校验通过后再调用。
     */
    public static DatasourceTypeProvider<?> get(DatasourceType type) {
        DatasourceTypeProvider<?> provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException(
                    "No DatasourceTypeProvider registered for type: " + type
                            + ". Available: " + providers.keySet());
        }
        return provider;
    }

    /** 是否已注册。给启动期校验 / 测试探测用。 */
    public static boolean isRegistered(DatasourceType type) {
        return providers.containsKey(type);
    }

    /** 当前注册的所有类型,只读视图。 */
    @SuppressWarnings("rawtypes")
    public static Map<DatasourceType, DatasourceTypeProvider> snapshot() {
        return providers;
    }

    /** 仅测试用:清空注册表。生产代码绝不应调用。 */
    public static synchronized void reset() {
        providers = Collections.emptyMap();
    }
}
