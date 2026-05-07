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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.RerankResult;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Cohere 风格 rerank REST 客户端。
 *
 * <p>请求格式(所有支持的 provider 共用):
 * <pre>
 *   POST {endpoint}
 *   Authorization: Bearer {apiKey}    ← apiKey 为空时不带此 header(自部署场景)
 *   Content-Type: application/json
 *   { "model": "...", "query": "...", "documents": [...], "top_n": N }
 * </pre>
 *
 * <p>响应格式(所有支持的 provider 共用):
 * <pre>
 *   { "results": [ { "index": int, "relevance_score": float }, ... ] }
 * </pre>
 *
 * <p>HTTP 走 Spring {@link RestClient}(Spring AI 推进点 —— 替代 JDK HttpClient,
 * 进入 Spring 生态,后续与 Spring AI 其他组件一致)。
 */
@Slf4j
public class GenericRerankClient implements RerankClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final int ERROR_BODY_PREVIEW_LIMIT = 300;

    private final RestClient restClient;
    private final String model;
    private final String endpoint;

    public GenericRerankClient(GenericProperties props) {
        if (props == null || props.getEndpoint() == null || props.getEndpoint().isBlank()) {
            throw new IllegalArgumentException("rerank endpoint is required");
        }
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new IllegalArgumentException("rerank model is required");
        }
        this.endpoint = props.getEndpoint();
        this.model = props.getModel();

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        rf.setReadTimeout((int) READ_TIMEOUT.toMillis());

        RestClient.Builder builder = RestClient.builder().requestFactory(rf);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            // 自部署 Xinference / LocalAI 可能不要 key —— 留空时不带 Authorization header
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
        }
        this.restClient = builder.build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        int effectiveTopN = topN <= 0 || topN > documents.size() ? documents.size() : topN;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", effectiveTopN);

        // 响应读 String + JsonUtils.parseTree 解析: RestClient.builder() 不带 Spring Boot auto-config
        // 的 Jackson,直接 .body(JsonNode.class) 在某些 provider 响应下会抛 "Type definition error"。
        // 请求侧 RestClient.body(map) 走默认 Jackson converter 是没问题的(只有 JsonNode 反序列化有 bug)。
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("rerank request failed: " + e.getMessage(), e);
        }
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            return parseResults(JsonUtils.parseTree(responseBody));
        } catch (Exception e) {
            throw new IllegalStateException("rerank response parse failed: " + e.getMessage()
                    + ", body=" + StringUtils.abbreviate(responseBody, ERROR_BODY_PREVIEW_LIMIT), e);
        }
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public HealthStatus healthCheck() {
        try {
            // 最小代价 ping
            rerank("health", List.of("ok"), 1);
            return HealthStatus.healthy();
        } catch (Exception e) {
            return HealthStatus.unhealthy("rerank failed: " + e.getMessage());
        }
    }

    /**
     * 解析响应。Cohere/智谱/DashScope-compatible 都返回 {@code {"results": [{"index", "relevance_score"}, ...]}}。
     */
    private static List<RerankResult> parseResults(JsonNode root) {
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return List.of();
        }
        List<RerankResult> out = new ArrayList<>(results.size());
        for (JsonNode r : results) {
            int index = r.path("index").asInt(-1);
            double score = r.path("relevance_score").asDouble(0.0);
            if (index >= 0) {
                out.add(new RerankResult(index, score));
            }
        }
        return out;
    }
}
