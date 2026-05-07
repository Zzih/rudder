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

import java.util.Map;

/**
 * Metadata client provider 工厂。实现需在
 * {@code META-INF/services/io.github.zzih.rudder.metadata.api.spi.MetadataClientFactory}
 * 中登记,由 {@code MetadataPluginManager} 通过 {@link java.util.ServiceLoader} 发现。必须提供无参构造函数。
 */
public interface MetadataClientFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override
    default String family() {
        return "metadata";
    }

    /**
     * 根据平台配置页填入的参数构造 MetadataClient 实例。
     * 无需配置的 provider(如 JDBC)可忽略 config。
     */
    MetadataClient create(ProviderContext ctx, Map<String, String> config);

    /**
     * 默认 testConnection: 创建实例 + 调 healthCheck()。Metadata 的查询方法都需要 DataSourceInfo,
     * 而 testConnection 拿不到具体 datasource 上下文,所以只验证 client 自身能起来 (URL / token / endpoint)。
     */
    @Override
    default TestResult testConnection(ProviderContext ctx, Map<String, String> config) {
        long start = System.currentTimeMillis();
        try (MetadataClient client = create(ctx, config)) {
            HealthStatus status = client.healthCheck();
            long elapsed = System.currentTimeMillis() - start;
            // healthCheck 默认 UNKNOWN(没 override 即"客户端能 new 出来就算通"),也算 success
            return status.state() == HealthStatus.State.UNHEALTHY
                    ? TestResult.failed(status.message() != null ? status.message() : "unhealthy", elapsed)
                    : TestResult.success(elapsed);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
