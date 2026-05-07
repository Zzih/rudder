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

import java.net.URI;

/**
 * OAuth / OIDC 重定向 + callback 流程的 SSO 认证器。OIDC 实现此接口。
 *
 * <p>典型流程:
 * <pre>
 * 1. controller 调 {@link #buildSignInUrl(AuthSource, String)} 拿到 IdP 跳转 URL,
 *    把 state 写一次性凭证(防 CSRF / 重放),302 浏览器到 IdP
 * 2. 用户在 IdP 登录后被 302 回 callback
 * 3. controller 校验 state 后调 {@link #handleCallback(AuthSource, String, String)},
 *    内部完成 code → token → userinfo → user 合并 → JWT
 * </pre>
 */
public interface SsoAuthenticator extends Authenticator {

    /** 生成跳转到 IdP 授权页的 URL。{@code state} 由 controller 生成,本方法只是拼参数。 */
    String buildSignInUrl(AuthSource source, String state);

    /**
     * 处理 IdP 回调:用 code 换 token、拿 userinfo、合并/创建本地用户、签 JWT。
     *
     * @throws io.github.zzih.rudder.common.exception.BizException IdP 通信失败 / 校验失败
     */
    AuthService.AuthResult handleCallback(AuthSource source, String code, String state);

    /** callback 成功后浏览器要 302 到的前端地址(带 token)。具体协议字段在各实现的 source config 内。 */
    URI buildSuccessRedirect(AuthSource source, String token);

    /** callback 失败后浏览器要 302 到的前端地址(带 errorCode)。 */
    URI buildFailureRedirect(AuthSource source, String errorCode);
}
