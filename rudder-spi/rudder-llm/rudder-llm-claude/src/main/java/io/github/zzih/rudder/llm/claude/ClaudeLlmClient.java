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

package io.github.zzih.rudder.llm.claude;

import io.github.zzih.rudder.llm.api.springai.SpringAiBackedLlmClient;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

/**
 * Anthropic Claude 客户端,底层走 Spring AI {@link AnthropicChatModel}。
 * 原手写的 HTTP / SSE / JSON 解析 ~300 行全部交给 Spring AI 的 Anthropic 官方 SDK 接入。
 * Rudder 的 LlmClient 契约由 {@link SpringAiBackedLlmClient} 基类兜住。
 */
public class ClaudeLlmClient extends SpringAiBackedLlmClient {

    public ClaudeLlmClient(ClaudeProperties properties) {
        super(buildChatModel(properties));
    }

    private static AnthropicChatModel buildChatModel(ClaudeProperties props) {
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .build();
        return AnthropicChatModel.builder().options(options).build();
    }
}
