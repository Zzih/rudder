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

package io.github.zzih.rudder.llm.openai;

import io.github.zzih.rudder.llm.api.springai.SpringAiBackedLlmClient;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

/**
 * OpenAI 协议兼容客户端(覆盖 OpenAI / DeepSeek / Qwen(OpenAI 模式)/ Moonshot / vLLM / Ollama 等)。
 * 底层走 Spring AI {@link OpenAiChatModel}。原手写 ~430 行(HTTP/SSE/tool_calls ↔ Claude 格式转换)
 * 全部交给 Spring AI 处理,Rudder 的 LlmClient 契约由 {@link SpringAiBackedLlmClient} 基类兜住。
 */
public class OpenaiLlmClient extends SpringAiBackedLlmClient {

    public OpenaiLlmClient(OpenaiProperties properties) {
        super(buildChatModel(properties));
    }

    private static OpenAiChatModel buildChatModel(OpenaiProperties props) {
        // Spring AI 2.0.0-M5 起底层 client 切到官方 openai-java SDK；旧的 OpenAiApi 类已删除。
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .build();
        return OpenAiChatModel.builder().openAiClient(client).options(options).build();
    }
}
