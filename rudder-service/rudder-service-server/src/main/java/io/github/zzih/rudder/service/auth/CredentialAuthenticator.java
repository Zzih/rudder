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

/**
 * 用户名 + 密码直接登录的认证器。PASSWORD / LDAP 实现此接口。
 *
 * <p>OIDC 不实现此接口(走 {@link SsoAuthenticator} 重定向流程)。
 */
public interface CredentialAuthenticator extends Authenticator {

    /**
     * 校验账号密码,成功返回包含 JWT 的 {@link AuthService.AuthResult}。
     *
     * @throws io.github.zzih.rudder.common.exception.BizException 用户不存在 / 密码错 / 账号被禁等
     */
    AuthService.AuthResult authenticate(AuthSource source, String username, String password);
}
