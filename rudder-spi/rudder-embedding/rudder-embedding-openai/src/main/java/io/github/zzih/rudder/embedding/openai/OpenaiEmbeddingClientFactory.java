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

package io.github.zzih.rudder.embedding.openai;

import io.github.zzih.rudder.embedding.api.EmbeddingClient;
import io.github.zzih.rudder.embedding.api.spi.EmbeddingClientFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

/** OpenAI 兼容 embedding provider 工厂。 */
@AutoService(EmbeddingClientFactory.class)
public class OpenaiEmbeddingClientFactory implements EmbeddingClientFactory {

    public static final String PROVIDER = "OPENAI";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("apiKey").label("API Key").type("password")
                        .required(true).placeholder("sk-xxxxxxxx")
                        .build(),
                PluginParamDefinition.builder()
                        .name("model").label("Model").type("input")
                        .required(true).placeholder("text-embedding-3-small")
                        .defaultValue("text-embedding-3-small")
                        .build(),
                PluginParamDefinition.builder()
                        .name("baseUrl").label("Base URL").type("input")
                        .required(false).placeholder("https://api.openai.com")
                        .defaultValue("https://api.openai.com")
                        .build(),
                PluginParamDefinition.builder()
                        .name("dimensions").label("Vector Dimensions").type("input")
                        .required(true).placeholder("1536")
                        .defaultValue("1536")
                        .build());
    }

    @Override
    public EmbeddingClient create(ProviderContext ctx, Map<String, String> config) {
        OpenaiProperties props = new OpenaiProperties();
        props.setApiKey(config.getOrDefault("apiKey", ""));
        props.setModel(config.getOrDefault("model", "text-embedding-3-small"));
        props.setBaseUrl(config.getOrDefault("baseUrl", "https://api.openai.com"));
        int dim = AbstractConfigurablePluginRegistry.parseIntOrDefault(config.get("dimensions"), 1536);
        return new OpenaiEmbeddingClient(props, dim);
    }
}
