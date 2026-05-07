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

/**
 * 认证器顶层契约。每种 {@link AuthSourceType} 一个 {@code @Component} 实现。
 *
 * <p>具体登录动作落在两个子接口:
 * <ul>
 *   <li>{@link CredentialAuthenticator} —— 用户名 + 密码直接登录(PASSWORD / LDAP)</li>
 *   <li>{@link SsoAuthenticator} —— OAuth/OIDC 重定向 + callback 流程</li>
 * </ul>
 *
 * <p>同一 {@code Authenticator} 可同时实现两个子接口(理论上,虽然当前没有这种实现)。
 * Controller 通过 {@link AuthenticatorDispatcher} 按 type 派发到对应实例。
 */
public interface Authenticator {

    AuthSourceType type();

    /**
     * 探活当前 source 的配置是否可达。OIDC: well-known endpoint; LDAP: bind 测试; PASSWORD: 永远健康。
     */
    HealthStatus testConnection(AuthSource source);
}
