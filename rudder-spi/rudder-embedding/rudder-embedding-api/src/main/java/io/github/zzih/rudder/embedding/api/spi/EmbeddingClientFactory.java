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

package io.github.zzih.rudder.embedding.api.spi;

import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.TestResult;

import java.util.List;
import java.util.Map;

/**
 * Embedding provider 工厂。实现在
 * {@code META-INF/services/io.github.zzih.rudder.embedding.api.spi.EmbeddingClientFactory} 登记。
 */
public interface EmbeddingClientFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override
    default String family() {
        return "embedding";
    }

    EmbeddingClient create(ProviderContext ctx, Map<String, String> config);

    /** 默认 testConnection: 创建实例 + 跑一个最小 embed("ping") 验证 endpoint + apiKey + model 都对。 */
    @Override
    default TestResult testConnection(ProviderContext ctx, Map<String, String> config) {
        long start = System.currentTimeMillis();
        try (EmbeddingClient client = create(ctx, config)) {
            List<float[]> result = client.embedBatch(List.of("ping"));
            long elapsed = System.currentTimeMillis() - start;
            if (result == null || result.isEmpty() || result.get(0) == null || result.get(0).length == 0) {
                return TestResult.failed("embed returned empty vector", elapsed);
            }
            return TestResult.success(elapsed);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
