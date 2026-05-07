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

package io.github.zzih.rudder.common.utils.net;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 通用 HTTP 工具类，基于 Java 11+ HttpClient。
 * 提供共享单例、常用 GET/POST/PATCH/DELETE 快捷方法。
 */
public class HttpUtils {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private HttpUtils() {
    }

    /**
     * 从请求中解析客户端真实 IP。
     * 优先读取 {@code X-Forwarded-For}（多级代理时取第一个），次选 {@code X-Real-IP}，
     * 均无则回退到 {@link HttpServletRequest#getRemoteAddr()}。
     * <p>
     * 注意：反向代理必须可信，否则直连客户端可伪造这些头。
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 构造 RFC 5987 风格的 {@code Content-Disposition: attachment} 头值。
     * 同时给出 ASCII 安全的 {@code filename=} 兜底老浏览器,以及 UTF-8 编码的 {@code filename*=}
     * 让现代浏览器拿到原始文件名(中文等非 ASCII)。
     */
    public static String contentDispositionAttachment(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
    }

    // ==================== GET ====================

    public static String get(String url) {
        return get(url, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, String> headers) {
        return get(url, headers, DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        return sendRequest(builder.build());
    }

    // ==================== POST ====================

    public static String postJson(String url, String body) {
        return postJson(url, body, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String postJson(String url, String body, Map<String, String> headers) {
        return postJson(url, body, headers, DEFAULT_TIMEOUT);
    }

    public static String postJson(String url, String body, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        headers.forEach(builder::header);
        return sendRequest(builder.build());
    }

    // ==================== PATCH ====================

    public static String patchJson(String url, String body) {
        return patchJson(url, body, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String patchJson(String url, String body, Map<String, String> headers) {
        return patchJson(url, body, headers, DEFAULT_TIMEOUT);
    }

    public static String patchJson(String url, String body, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        headers.forEach(builder::header);
        return sendRequest(builder.build());
    }

    // ==================== DELETE ====================

    public static String delete(String url) {
        return delete(url, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String delete(String url, Map<String, String> headers) {
        return delete(url, headers, DEFAULT_TIMEOUT);
    }

    public static String delete(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .DELETE();
        headers.forEach(builder::header);
        return sendRequest(builder.build());
    }

    // ==================== 通用发送 ====================

    /**
     * 发送任意 HttpRequest，返回响应体字符串。
     * HTTP 状态码非 2xx 时抛出 RuntimeException。
     */
    private static String sendRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + request.uri() + " - " + e.getMessage(), e);
        }
    }
}
