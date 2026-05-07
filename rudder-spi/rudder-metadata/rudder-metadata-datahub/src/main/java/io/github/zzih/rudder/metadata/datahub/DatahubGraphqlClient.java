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

package io.github.zzih.rudder.metadata.datahub;

import io.github.zzih.rudder.common.enums.error.SpiErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight GraphQL client for DataHub GMS API.
 * Uses project-shared HttpUtils + JsonUtils.
 */
@Slf4j
public class DatahubGraphqlClient {

    private final String graphqlUrl;
    private final Map<String, String> headers;

    public DatahubGraphqlClient(String baseUrl, String token) {
        this.graphqlUrl = baseUrl + "/api/graphql";
        this.headers = Map.of("Authorization", "Bearer " + token);
    }

    /**
     * Execute a GraphQL query and return the "data" node of the response.
     *
     * @throws BizException with {@link SpiErrorCode#PROVIDER_EXECUTION_FAILED} if the response contains errors or is malformed
     */
    public JsonNode execute(String query, Map<String, Object> variables) {
        ObjectNode body = JsonUtils.createObjectNode();
        body.put("query", query);
        body.set("variables", JsonUtils.toJsonNode(variables));

        String responseBody = HttpUtils.postJson(graphqlUrl, JsonUtils.toJson(body), headers);

        JsonNode root = JsonUtils.parseTree(responseBody);

        if (root.has("errors") && !root.get("errors").isEmpty()) {
            String errorMsg = root.get("errors").get(0).path("message").asText("Unknown GraphQL error");
            throw new BizException(SpiErrorCode.PROVIDER_EXECUTION_FAILED, "DataHub GraphQL: " + errorMsg);
        }

        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            throw new BizException(SpiErrorCode.PROVIDER_EXECUTION_FAILED, "DataHub response missing 'data' field");
        }
        return data;
    }
}
