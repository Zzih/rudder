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

package io.github.zzih.rudder.service.auth;

import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Component;

/**
 * 本地账号认证。直接调 {@link AuthService#login(String, String)} —— 校验 BCrypt + 签 JWT。
 *
 * <p>不读取 {@code source.configJson}(PASSWORD source 配置永远为 NULL)。
 */
@Component
public class PasswordAuthenticator extends AbstractAuthenticator implements CredentialAuthenticator {

    public PasswordAuthenticator(AuthService authService) {
        super(authService);
    }

    @Override
    public AuthSourceType type() {
        return AuthSourceType.PASSWORD;
    }

    @Override
    public AuthService.AuthResult authenticate(AuthSource source, String username, String password) {
        return authService.login(username, password);
    }

    @Override
    public HealthStatus testConnection(AuthSource source) {
        // PASSWORD 是本地登录,只要能查到 t_r_user 就健康。这里直接 healthy。
        return HealthStatus.healthy();
    }
}
