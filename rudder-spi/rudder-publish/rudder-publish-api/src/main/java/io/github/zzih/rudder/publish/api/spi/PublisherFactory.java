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

package io.github.zzih.rudder.publish.api.spi;

import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

/**
 * Publisher provider 工厂。实现需在
 * {@code META-INF/services/io.github.zzih.rudder.publish.api.spi.PublisherFactory}
 * 中登记,由 {@code PublishPluginManager} 通过 {@link java.util.ServiceLoader} 发现。必须提供无参构造函数。
 *
 * @param <P> provider 配置 POJO 类型
 */
public interface PublisherFactory<P> extends ConfigurablePluginProviderFactory<ProviderContext, P> {

    @Override
    default String type() {
        return "publish";
    }

    Publisher create(ProviderContext ctx, P props);
}
