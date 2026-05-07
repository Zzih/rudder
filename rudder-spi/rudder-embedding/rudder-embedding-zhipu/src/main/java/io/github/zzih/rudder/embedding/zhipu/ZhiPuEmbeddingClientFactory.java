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

package io.github.zzih.rudder.embedding.zhipu;

import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.embedding.api.spi.EmbeddingClientFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

@AutoService(EmbeddingClientFactory.class)
public class ZhiPuEmbeddingClientFactory implements EmbeddingClientFactory {

    public static final String PROVIDER = "ZHIPU";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("apiKey").label("API Key").type("password")
                        .required(true).placeholder("spi.embedding.zhipu.apiKey.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("model").label("Model").type("input")
                        .required(true).placeholder("embedding-3")
                        .defaultValue("embedding-3")
                        .build(),
                PluginParamDefinition.builder()
                        .name("dimensions").label("Vector Dimensions").type("input")
                        .required(true).placeholder("1024")
                        .defaultValue("1024")
                        .build(),
                PluginParamDefinition.builder()
                        .name("baseUrl").label("Base URL").type("input")
                        .required(false).placeholder("https://open.bigmodel.cn/api/paas/v4")
                        .defaultValue("https://open.bigmodel.cn/api/paas/v4")
                        .build());
    }

    @Override
    public EmbeddingClient create(ProviderContext ctx, Map<String, String> config) {
        ZhiPuProperties props = new ZhiPuProperties();
        props.setApiKey(config.getOrDefault("apiKey", ""));
        String model = config.get("model");
        if (model != null && !model.isBlank()) {
            props.setModel(model.trim());
        }
        String baseUrl = config.get("baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) {
            props.setBaseUrl(baseUrl.trim());
        }
        props.setDimensions(AbstractConfigurablePluginRegistry.parseIntOrDefault(
                config.get("dimensions"), props.getDimensions()));
        return new ZhiPuEmbeddingClient(props);
    }
}
