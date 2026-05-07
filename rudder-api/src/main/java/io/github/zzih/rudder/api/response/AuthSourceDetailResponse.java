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
import io.github.zzih.rudder.service.auth.config.LdapSourceConfig;
import io.github.zzih.rudder.service.auth.config.OidcSourceConfig;
import io.github.zzih.rudder.service.auth.config.SourceConfig;
import io.github.zzih.rudder.service.auth.dto.AuthSourceDTO;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Admin 详情。{@link #config} 是结构化对象(SourceConfig 子类),敏感字段(clientSecret / bindPassword)
 * 已替换为 {@value MASK} 占位符。前端编辑时若用户不改这些字段,提交 update 时不带 config(或清掉敏感字段)
 * 让后端保留旧值;v1 简化方案不做"占位符等于不变"的隐式逻辑。
 */
@Data
public class AuthSourceDetailResponse {

    /** 脱敏占位符。 */
    public static final String MASK = "••••••";

    private Long id;
    private String name;
    private AuthSourceType type;
    private Boolean enabled;
    private Boolean isSystem;
    private Integer priority;
    /** 协议特定配置(已 mask);PASSWORD 行为 null。 */
    private SourceConfig config;
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

    /** 按 type 反序列化为对应 config 类,并把敏感字段替换为 {@link #MASK}。 */
    private static SourceConfig parseAndMask(String json, AuthSourceType type) {
        if (json == null || json.isBlank() || type == null || type == AuthSourceType.PASSWORD) {
            return null;
        }
        try {
            return switch (type) {
                case OIDC -> {
                    OidcSourceConfig cfg = JsonUtils.fromJson(json, OidcSourceConfig.class);
                    if (cfg.getClientSecret() != null && !cfg.getClientSecret().isEmpty()) {
                        cfg.setClientSecret(MASK);
                    }
                    yield cfg;
                }
                case LDAP -> {
                    LdapSourceConfig cfg = JsonUtils.fromJson(json, LdapSourceConfig.class);
                    if (cfg.getBindPassword() != null && !cfg.getBindPassword().isEmpty()) {
                        cfg.setBindPassword(MASK);
                    }
                    yield cfg;
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
