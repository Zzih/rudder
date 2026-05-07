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

package io.github.zzih.rudder.llm.deepseek;

import io.github.zzih.rudder.llm.api.springai.SpringAiBackedLlmClient;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

/** DeepSeek 原生客户端(走 Spring AI native DeepSeek driver,非 OpenAI 协议兼容)。 */
public class DeepSeekLlmClient extends SpringAiBackedLlmClient {

    public DeepSeekLlmClient(DeepSeekProperties properties) {
        super(buildChatModel(properties));
    }

    private static DeepSeekChatModel buildChatModel(DeepSeekProperties props) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .build();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .build();
        return DeepSeekChatModel.builder().deepSeekApi(api).defaultOptions(options).build();
    }
}
