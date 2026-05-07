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

package io.github.zzih.rudder.version.api;

import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.util.Map;

/**
 * 版本存储 provider 工厂。实现需在 {@code META-INF/services/io.github.zzih.rudder.version.api.VersionStoreFactory}
 * 中登记,由 {@code VersionPluginManager} 通过 {@link java.util.ServiceLoader} 发现。
 *
 * <p>实现必须提供无参构造函数。宿主依赖通过 {@link ProviderContext} 在 {@link #create} 时传入,
 * 严禁使用 {@code @Autowired} / {@code @Value} / 直接访问宿主单例。
 */
public interface VersionStoreFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override
    default String family() {
        return "version";
    }

    /** 用运行时上下文与配置参数构造一个 {@link VersionStore} 实例。 */
    VersionStore create(ProviderContext ctx, Map<String, String> config);
}
