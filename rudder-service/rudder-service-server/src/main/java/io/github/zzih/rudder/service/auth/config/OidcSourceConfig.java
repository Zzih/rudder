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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OIDC source 的配置 POJO,跟 {@code t_r_auth_source.config_json}(type=OIDC) 一一对应。
 *
 * <p>{@link #clientSecret} 在落库前由 service 层用 {@code RUDDER_ENCRYPT_KEY} AES 加密;
 * 读出后由 service 层解密再传给 Authenticator,这里持有的是明文。
 */
@Data
public final class OidcSourceConfig implements SourceConfig {

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String redirectUri;

    /** IdP issuer,主要用于 OIDC 校验 id_token 的 iss 字段。 */
    private String issuer;

    @NotBlank
    private String authorizationUri;

    @NotBlank
    private String tokenUri;

    @NotBlank
    private String userInfoUri;

    /** 默认 {@code openid profile email}。 */
    private String scopes = "openid profile email";

    /** 登录成功后前端回跳地址,token 拼在 query。例: {@code http://localhost:5173/sso/callback} */
    @NotBlank
    private String frontendRedirectUrl;
}
