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

import io.github.zzih.rudder.spi.api.model.ProviderMetadata;

/**
 * 所有 SPI 工厂的最小公共基接口。只承载与"实例构造"无关、所有 SPI 都需要的元信息。
 *
 * <p>{@link AbstractPluginRegistry} 以此类型作为上界，可直接调用 {@link #priority()}
 * 做冲突裁决，取代之前的反射查找。
 */
public interface PluginProviderFactory {

    /** 多 provider 同 key 冲突时优先级高者胜出；默认 0。 */
    default int priority() {
        return 0;
    }

    /** Provider 元数据（version / description / author / since / docsUrl）。默认为空。 */
    default ProviderMetadata metadata() {
        return ProviderMetadata.empty();
    }
}
