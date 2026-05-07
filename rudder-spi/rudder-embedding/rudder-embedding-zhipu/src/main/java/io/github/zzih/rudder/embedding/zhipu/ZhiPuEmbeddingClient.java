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
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 智谱 AI embedding 客户端 —— 底层走 Spring AI {@link OpenAiEmbeddingModel}。
 *
 * <p>ZhiPu 的请求/响应体完全兼容 OpenAI embeddings 协议,只有路径不同
 * ({@code /api/paas/v4/embeddings} 而非 {@code /v1/embeddings})。
 * 配置 {@code baseUrl=https://open.bigmodel.cn/api/paas/v4}，SDK 内部默认拼 {@code /embeddings}
 * 即可拼出完整 URL,无需再覆写 path。
 *
 * <p>参考:<a href="https://docs.bigmodel.cn/api-reference/模型-api/文本嵌入">文本嵌入</a>。
 */
@Slf4j
public class ZhiPuEmbeddingClient implements EmbeddingClient {

    private final OpenAiEmbeddingModel embeddingModel;
    private final String model;
    private final int dimensions;

    public ZhiPuEmbeddingClient(ZhiPuProperties properties) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimensions())
                .build();
        this.embeddingModel = new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
        this.model = properties.getModel();
        this.dimensions = properties.getDimensions();
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
            return HealthStatus.unhealthy("ZhiPu embedding failed: " + e.getMessage());
        }
    }
}
