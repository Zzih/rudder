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

import io.github.zzih.rudder.llm.api.springai.SpringAiBackedLlmClient;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Ollama 本地 LLM 客户端。底层走 Spring AI {@link OllamaChatModel}。
 * 适合离线 / 私域部署,Ollama 默认监听 {@code http://localhost:11434},无需 API Key。
 */
public class OllamaLlmClient extends SpringAiBackedLlmClient {

    public OllamaLlmClient(OllamaProperties properties) {
        super(buildChatModel(properties));
    }

    private static OllamaChatModel buildChatModel(OllamaProperties props) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(props.getBaseUrl())
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(props.getModel())
                .numPredict(props.getNumPredict())
                .build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }
}
