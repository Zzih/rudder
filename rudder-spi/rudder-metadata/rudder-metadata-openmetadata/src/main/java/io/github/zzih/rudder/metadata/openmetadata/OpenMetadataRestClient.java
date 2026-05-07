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

package io.github.zzih.rudder.metadata.openmetadata;

import io.github.zzih.rudder.common.enums.error.SpiErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight REST client for OpenMetadata. Auth via Bearer token (JWT).
 * All endpoints under {@code {baseUrl}/api/v1}.
 */
@Slf4j
public class OpenMetadataRestClient {

    private final String apiBase;
    private final Map<String, String> headers;

    public OpenMetadataRestClient(String baseUrl, String token) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiBase = normalized + "/api/v1";
        this.headers = Map.of("Authorization", "Bearer " + token);
    }

    /** GET {apiBase}{path}. {@code path} must start with "/". Returns parsed JSON or {@code null} on 404. */
    public JsonNode get(String path) {
        String url = apiBase + path;
        try {
            String body = HttpUtils.get(url, headers);
            return JsonUtils.parseTree(body);
        } catch (Exception e) {
            // HttpUtils throws RuntimeException with message "HTTP <code>: <body>" on non-2xx.
            // 404 surfaces as null so callers can fall back; other codes propagate.
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.startsWith("HTTP 404")) {
                return null;
            }
            throw new BizException(SpiErrorCode.PROVIDER_EXECUTION_FAILED,
                    "OpenMetadata GET " + url + " failed: " + msg);
        }
    }

    /** URL-encode a path segment (FQN contains dots that should stay, but spaces / special chars encoded). */
    public static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
