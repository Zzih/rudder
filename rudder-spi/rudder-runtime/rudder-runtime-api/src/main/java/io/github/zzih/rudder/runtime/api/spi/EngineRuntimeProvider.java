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

package io.github.zzih.rudder.runtime.api.spi;

import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

/**
 * Runtime provider SPI(LOCAL / ALIYUN / AWS)。每个 provider 模块暴露一个 {@code EngineRuntimeProvider},
 * 声明 UI 表单字段({@link #params()})并按用户填入的参数构造唯一一个 {@link EngineRuntime}。
 *
 * @param <P> provider 配置 POJO 类型
 */
public interface EngineRuntimeProvider<P> extends ConfigurablePluginProviderFactory<ProviderContext, P> {

    @Override
    default String type() {
        return "runtime";
    }

    EngineRuntime create(ProviderContext ctx, P props);

    /** 切换 provider / 关停时清理共享资源(SDK client 池等)。默认 no-op。 */
    default void closeResources() {
    }
}
