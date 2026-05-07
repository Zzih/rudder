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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.rerank.api.RerankResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 端到端测 {@link GenericRerankClient} 的 HTTP 请求构造 + 响应解析。用 JDK {@link HttpServer}
 * 起一个本地 mock 端口,真实走一遍 RestClient 调用,避免 mock RestClient 内部细节(那种 mock 容易和实现耦合)。
 */
class GenericRerankClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GenericRerankClient newClient(String model) {
        GenericProperties props = new GenericProperties();
        props.setApiKey("test-key");
        props.setEndpoint("http://127.0.0.1:" + port + "/v2/rerank");
        props.setModel(model);
        return new GenericRerankClient(props);
    }

    @Test
    @DisplayName("正常路径: 解析 results[].index/relevance_score, 按 score 顺序返回")
    void rerank_parsesResultsByOrder() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        server.createContext("/v2/rerank", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, """
                    {
                      "results": [
                        {"index": 2, "relevance_score": 0.94},
                        {"index": 0, "relevance_score": 0.61},
                        {"index": 1, "relevance_score": 0.12}
                      ]
                    }
                    """);
        });

        GenericRerankClient client = newClient("rerank-v3.5");
        List<RerankResult> ranked = client.rerank("flink window",
                List.of("doc-A", "doc-B", "doc-C"), 3);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).index()).isEqualTo(2);
        assertThat(ranked.get(0).score()).isEqualTo(0.94);
        assertThat(ranked.get(1).index()).isEqualTo(0);
        assertThat(ranked.get(2).index()).isEqualTo(1);

        // 校验请求体: 含 model / query / documents / top_n
        assertThat(capturedBody.get()).contains("\"model\":\"rerank-v3.5\"");
        assertThat(capturedBody.get()).contains("\"query\":\"flink window\"");
        assertThat(capturedBody.get()).contains("\"top_n\":3");
        // 校验鉴权 header
        assertThat(capturedAuth.get()).isEqualTo("Bearer test-key");
    }

    @Test
    @DisplayName("apiKey 为空时不带 Authorization header (自部署场景)")
    void rerank_emptyApiKeyOmitsAuthHeader() {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        server.createContext("/v2/rerank", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, "{\"results\":[]}");
        });
        GenericProperties props = new GenericProperties();
        props.setApiKey("");
        props.setEndpoint("http://127.0.0.1:" + port + "/v2/rerank");
        props.setModel("bge-reranker-v2-m3");

        new GenericRerankClient(props).rerank("q", List.of("x"), 1);

        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    @DisplayName("空 documents 直接返回空 list, 不打 HTTP 调用")
    void rerank_emptyDocumentsSkipsHttp() {
        AtomicReference<Boolean> hit = new AtomicReference<>(false);
        server.createContext("/v2/rerank", exchange -> {
            hit.set(true);
            sendJson(exchange, 200, "{\"results\":[]}");
        });

        List<RerankResult> ranked = newClient("rerank-v3.5").rerank("q", List.of(), 5);

        assertThat(ranked).isEmpty();
        assertThat(hit.get()).isFalse();
    }

    @Test
    @DisplayName("HTTP 5xx 抛 IllegalStateException 包含状态码")
    void rerank_serverErrorThrows() {
        server.createContext("/v2/rerank",
                exchange -> sendJson(exchange, 500, "{\"error\":\"internal\"}"));

        assertThatThrownBy(() -> newClient("rerank-v3.5").rerank("q", List.of("d"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rerank request failed");
    }

    @Test
    @DisplayName("topN <= 0 自动用 documents.size()")
    void rerank_topNZeroUsesAllDocs() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v2/rerank", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 200, "{\"results\":[]}");
        });

        newClient("rerank-v3.5").rerank("q", List.of("a", "b", "c"), 0);

        assertThat(capturedBody.get()).contains("\"top_n\":3");
    }

    @Test
    @DisplayName("modelId() 返回构造时配的 model")
    void modelId_returnsConfiguredModel() {
        assertThat(newClient("rerank-multilingual-v3.0").modelId())
                .isEqualTo("rerank-multilingual-v3.0");
    }

    @Test
    @DisplayName("缺 endpoint 直接抛, 早暴露错误")
    void constructor_missingEndpointThrows() {
        GenericProperties p = new GenericProperties();
        p.setModel("x");
        assertThatThrownBy(() -> new GenericRerankClient(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    @DisplayName("缺 model 直接抛, 早暴露错误")
    void constructor_missingModelThrows() {
        GenericProperties p = new GenericProperties();
        p.setEndpoint("http://x/v2/rerank");
        assertThatThrownBy(() -> new GenericRerankClient(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    private static class JsonHandler implements HttpHandler {

        private final int status;
        private final String body;

        JsonHandler(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, status, body);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
