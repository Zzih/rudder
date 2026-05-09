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

package io.github.zzih.rudder.llm.ollama;

import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.spi.LlmClientFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(LlmClientFactory.class)
public class OllamaLlmClientFactory implements LlmClientFactory<OllamaProperties> {

    public static final String PROVIDER = "OLLAMA";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<OllamaProperties> propertiesClass() {
        return OllamaProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("baseUrl").label("Base URL").type("input")
                        .required(true).placeholder("http://localhost:11434")
                        .defaultValue("http://localhost:11434")
                        .build(),
                PluginParamDefinition.builder()
                        .name("model").label("Model").type("input")
                        .required(true).placeholder("llama3.1")
                        .defaultValue("llama3.1")
                        .build(),
                PluginParamDefinition.builder()
                        .name("numPredict").label("Num Predict (max tokens)").type("input")
                        .required(false).placeholder("4096")
                        .defaultValue("4096")
                        .build());
    }

    @Override
    public LlmClient create(ProviderContext ctx, OllamaProperties props) {
        return new OllamaLlmClient(props);
    }
}
