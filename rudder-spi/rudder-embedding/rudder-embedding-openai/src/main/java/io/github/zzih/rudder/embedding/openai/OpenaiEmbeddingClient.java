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
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 协议兼容 embedding 客户端(OpenAI / DashScope / vLLM / Ollama 等)。
 * 底层走 Spring AI {@link OpenAiEmbeddingModel},原手写 HTTP/JSON ~100 行已不需要。
 */
@Slf4j
public class OpenaiEmbeddingClient implements EmbeddingClient {

    private final OpenAiEmbeddingModel embeddingModel;
    private final String model;
    private final int dimensions;

    public OpenaiEmbeddingClient(OpenaiProperties properties, int dimensions) {
        // Spring AI 2.0.0-M5：底层 client 切到官方 openai-java SDK；旧的 OpenAiApi 类已删除。
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(dimensions)
                .build();
        this.embeddingModel = new OpenAiEmbeddingModel(client,
                org.springframework.ai.document.MetadataMode.EMBED, options);
        this.model = properties.getModel();
        this.dimensions = dimensions;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse resp = embeddingModel.embedForResponse(texts);
        return resp.getResults().stream().map(r -> r.getOutput()).toList();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public HealthStatus healthCheck() {
        try {
            embed("health");
            return HealthStatus.healthy();
        } catch (Exception e) {
            return HealthStatus.unhealthy("embedding failed: " + e.getMessage());
        }
    }
}
