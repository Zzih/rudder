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

package io.github.zzih.rudder.vector.api;

import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.spi.api.model.TestResult;

import java.util.Map;

/**
 * VectorStore provider 工厂。
 * <p>
 * 通过 {@code META-INF/services/io.github.zzih.rudder.vector.api.VectorStoreFactory}
 * (由 {@code @AutoService} 生成)注册;由 Admin 后台 "AI Config → Vector" 驱动装配。
 */
public interface VectorStoreFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override
    default String family() {
        return "vector";
    }

    VectorStore create(ProviderContext ctx, Map<String, String> config);

    /** 默认 testConnection: 创建实例 + 调 healthCheck()。 */
    @Override
    default TestResult testConnection(ProviderContext ctx, Map<String, String> config) {
        long start = System.currentTimeMillis();
        try (VectorStore store = create(ctx, config)) {
            HealthStatus status = store.healthCheck();
            long elapsed = System.currentTimeMillis() - start;
            return status.state() == HealthStatus.State.HEALTHY
                    ? TestResult.success(elapsed)
                    : TestResult.failed(status.message() != null ? status.message() : "unhealthy", elapsed);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
