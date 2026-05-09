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

package io.github.zzih.rudder.metadata.api.spi;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.spi.api.model.TestResult;

/**
 * Metadata client provider 工厂。实现需在
 * {@code META-INF/services/io.github.zzih.rudder.metadata.api.spi.MetadataClientFactory}
 * 中登记,由 {@code MetadataPluginManager} 通过 {@link java.util.ServiceLoader} 发现。必须提供无参构造函数。
 *
 * @param <P> provider 配置 POJO 类型
 */
public interface MetadataClientFactory<P> extends ConfigurablePluginProviderFactory<ProviderContext, P> {

    @Override
    default String type() {
        return "metadata";
    }

    MetadataClient create(ProviderContext ctx, P props);

    /** 默认 testConnection: 创建实例 + 调 healthCheck();metadata 的查询都要 DataSourceInfo,这里只验证客户端自身能起来。 */
    @Override
    default TestResult testConnection(ProviderContext ctx, P props) {
        long start = System.currentTimeMillis();
        try (MetadataClient client = create(ctx, props)) {
            HealthStatus status = client.healthCheck();
            long elapsed = System.currentTimeMillis() - start;
            return status.state() == HealthStatus.State.UNHEALTHY
                    ? TestResult.failed(status.message() != null ? status.message() : "unhealthy", elapsed)
                    : TestResult.success(elapsed);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
