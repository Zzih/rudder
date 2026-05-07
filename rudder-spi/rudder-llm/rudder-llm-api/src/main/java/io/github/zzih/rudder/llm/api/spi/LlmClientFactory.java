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

package io.github.zzih.rudder.llm.api.spi;

import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.model.LlmCompleteRequest;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.TestResult;

import java.util.Map;

/**
 * AI client provider 工厂。实现需在 {@code META-INF/services/io.github.zzih.rudder.llm.api.spi.LlmClientFactory}
 * 中登记,由 {@code LlmPluginManager} 通过 {@link java.util.ServiceLoader} 发现。必须提供无参构造函数。
 */
public interface LlmClientFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override
    default String family() {
        return "llm";
    }

    LlmClient create(ProviderContext ctx, Map<String, String> config);

    /**
     * 跑一个最小 ping 验证 endpoint + apiKey + model 都对。所有 4 个 LLM provider 走同一套 complete(),
     * 所以默认实现就够了;有特殊需求的 provider (比如想跳过 billing token) 自己 override。
     */
    @Override
    default TestResult testConnection(ProviderContext ctx, Map<String, String> config) {
        long start = System.currentTimeMillis();
        try (LlmClient client = create(ctx, config)) {
            LlmCompleteRequest probe = new LlmCompleteRequest();
            probe.setPrompt("ping");
            client.complete(probe);
            return TestResult.success(System.currentTimeMillis() - start);
        } catch (Exception e) {
            return TestResult.failed(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
