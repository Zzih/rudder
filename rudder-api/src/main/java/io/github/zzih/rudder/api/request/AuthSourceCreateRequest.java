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

package io.github.zzih.rudder.api.request;

import io.github.zzih.rudder.dao.enums.AuthSourceType;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新建 auth source。{@link #config} 为协议字段透传 map,后端按 {@link #type} 序列化为 JSON 落库:
 * <ul>
 *   <li>{@code OIDC}: {@code clientId / clientSecret / issuer / scopes / callbackBaseUrl}</li>
 *   <li>{@code LDAP}: {@code url / baseDn / bindDn / bindPassword / userSearchFilter / ...}</li>
 * </ul>
 * 字段级校验由 service 层 + Jackson 反序列化为具体 config POJO 时完成。
 *
 * <p>type=PASSWORD 不允许通过此接口新建(系统行只能存在一行,在 data.sql 种子里建)。
 */
@Data
public class AuthSourceCreateRequest {

    @NotBlank(message = "{validation.AuthSourceCreateRequest.name.required}")
    private String name;

    @NotNull(message = "{validation.AuthSourceCreateRequest.type.required}")
    private AuthSourceType type;

    private Map<String, Object> config;

    /** 默认 true。 */
    private Boolean enabled;

    /** 默认 0。 */
    private Integer priority;
}
