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

package io.github.zzih.rudder.api.response;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.dto.AuthSourceDTO;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Data;

/**
 * Admin 详情。{@link #config} 透传原 JSON 解析的 Map,敏感字段(clientSecret / bindPassword)
 * 已替换为 {@value MASK} 占位符。update 时若不带 config 后端保留旧值。
 */
@Data
public class AuthSourceDetailResponse {

    /** 脱敏占位符。 */
    public static final String MASK = "••••••";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private String name;
    private AuthSourceType type;
    private Boolean enabled;
    private Boolean isSystem;
    private Integer priority;
    /** 协议特定配置(已 mask)。 */
    private Map<String, Object> config;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AuthSourceDetailResponse from(AuthSourceDTO source) {
        AuthSourceDetailResponse r = new AuthSourceDetailResponse();
        r.setId(source.getId());
        r.setName(source.getName());
        r.setType(source.getType());
        r.setEnabled(source.getEnabled());
        r.setIsSystem(source.getIsSystem());
        r.setPriority(source.getPriority());
        r.setConfig(parseAndMask(source.getConfigJson(), source.getType()));
        r.setCreatedAt(source.getCreatedAt());
        r.setUpdatedAt(source.getUpdatedAt());
        return r;
    }

    /** 按 type 把敏感字段替换为 {@link #MASK} 后透传整个 config map。 */
    private static Map<String, Object> parseAndMask(String json, AuthSourceType type) {
        if (json == null || json.isBlank() || type == null) {
            return null;
        }
        try {
            Map<String, Object> map = JsonUtils.fromJson(json, MAP_TYPE);
            for (String sensitive : sensitiveFieldsFor(type)) {
                Object v = map.get(sensitive);
                if (v instanceof String s && !s.isEmpty()) {
                    map.put(sensitive, MASK);
                }
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] sensitiveFieldsFor(AuthSourceType type) {
        return switch (type) {
            case OIDC -> new String[]{"clientSecret"};
            case LDAP -> new String[]{"bindPassword"};
        };
    }
}
