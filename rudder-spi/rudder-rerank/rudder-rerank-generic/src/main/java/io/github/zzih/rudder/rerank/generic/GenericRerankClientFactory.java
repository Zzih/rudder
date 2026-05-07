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

package io.github.zzih.rudder.rerank.generic;

import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.spi.RerankClientFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

/**
 * Generic Cohere-style rerank provider 工厂。
 *
 * <p>覆盖所有"Cohere 兼容"的 rerank 服务:
 * <ul>
 *   <li>Cohere 官方 — endpoint {@code https://api.cohere.com/v2/rerank}, model {@code rerank-v3.5}</li>
 *   <li>智谱 — endpoint {@code https://open.bigmodel.cn/api/paas/v4/rerank}, model {@code rerank}</li>
 *   <li>DashScope 兼容接口 — endpoint {@code https://dashscope.aliyuncs.com/compatible-api/v1/reranks}, model {@code qwen3-rerank}</li>
 *   <li>Xinference 自部署 — endpoint {@code http://host:9997/v1/rerank}, model 任意</li>
 *   <li>LocalAI / 任何 Cohere 兼容自部署</li>
 * </ul>
 */
@AutoService(RerankClientFactory.class)
public class GenericRerankClientFactory implements RerankClientFactory {

    public static final String PROVIDER = "GENERIC";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("endpoint").label("Endpoint URL").type("input")
                        .required(true).placeholder("https://api.cohere.com/v2/rerank")
                        .build(),
                PluginParamDefinition.builder()
                        .name("model").label("Model").type("input")
                        .required(true).placeholder("rerank-v3.5")
                        .build(),
                PluginParamDefinition.builder()
                        .name("apiKey").label("API Key").type("password")
                        .required(false).placeholder("自部署服务可空")
                        .build());
        // topN 不在 provider 配置里,放 RAG pipeline 配置(t_r_ai_config type=RAG_PIPELINE)
    }

    @Override
    public RerankClient create(ProviderContext ctx, Map<String, String> config) {
        GenericProperties props = new GenericProperties();
        props.setApiKey(config.getOrDefault("apiKey", ""));
        props.setEndpoint(config.getOrDefault("endpoint", ""));
        props.setModel(config.getOrDefault("model", ""));
        return new GenericRerankClient(props);
    }
}
