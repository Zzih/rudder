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

package io.github.zzih.rudder.rerank.api.spi;

import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.TestResult;

import java.util.List;

/**
 * Rerank provider 工厂。实现在
 * {@code META-INF/services/io.github.zzih.rudder.rerank.api.spi.RerankClientFactory} 登记。
 *
 * @param <P> provider 配置 POJO 类型
 */
public interface RerankClientFactory<P> extends ConfigurablePluginProviderFactory<ProviderContext, P> {

    @Override
    default String type() {
        return "rerank";
    }

    RerankClient create(ProviderContext ctx, P props);

    /** 默认 testConnection: 创建实例 + 跑最小 rerank("ping",["doc"]) 验证 endpoint + apiKey + model。 */
    @Override
    default TestResult testConnection(ProviderContext ctx, P props) {
        long start = System.currentTimeMillis();
        try (RerankClient client = create(ctx, props)) {
            List<?> result = client.rerank("ping", List.of("ok"), 1);
            long elapsed = System.currentTimeMillis() - start;
            if (result == null || result.isEmpty()) {
                return TestResult.failed("rerank returned empty result", elapsed);
            }
            return TestResult.success(elapsed);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
