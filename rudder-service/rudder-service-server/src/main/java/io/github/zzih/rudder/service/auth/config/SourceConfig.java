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

package io.github.zzih.rudder.service.auth.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 不同 {@link io.github.zzih.rudder.dao.enums.AuthSourceType} 对应的配置 POJO 的多态基。
 *
 * <p>Jackson 在反序列化 {@code AuthSourceCreateRequest.config} 等字段时,根据兄弟字段
 * {@code type} 的值(EXTERNAL_PROPERTY)决定具体子类: {@code OIDC → OidcSourceConfig}、
 * {@code LDAP → LdapSourceConfig}。新增协议在 {@code permits} 与 {@code @JsonSubTypes} 各加一行,
 * 漏一边编译失败,强制保持一致。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OidcSourceConfig.class, name = "OIDC"),
        @JsonSubTypes.Type(value = LdapSourceConfig.class, name = "LDAP"),
})
public sealed interface SourceConfig
        permits OidcSourceConfig, LdapSourceConfig {
}
