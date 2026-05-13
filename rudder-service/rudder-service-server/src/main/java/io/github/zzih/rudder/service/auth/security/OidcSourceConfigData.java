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

package io.github.zzih.rudder.service.auth.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OIDC source 的 config_json 载体。Spring Security 用 {@code issuer} 自动 discovery
 * 其余 endpoint,redirect_uri 由 {@code {baseUrl}/login/oauth2/code/{sourceId}} 模板构造。
 * {@code clientSecret} 落库前由 AES 加密。
 */
@Data
public final class OidcSourceConfigData {

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String issuer;

    /** 逗号分隔 / 空格分隔均可。默认 {@code "openid,profile,email"}。 */
    private String scopes = "openid,profile,email";

    @NotBlank
    private String frontendRedirectUrl;
}
