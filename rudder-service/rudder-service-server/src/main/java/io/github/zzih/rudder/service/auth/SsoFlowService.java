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

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.service.coordination.token.OneShotTokenService;

import java.time.Duration;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSO 三段流程编排:start / callback / 凭证类登录。统一在这里处理 source lookup +
 * authenticator 派发 + state 校验,把 controller 的胶水代码聚拢,不让 entity 漏到 controller 层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoFlowService {

    private static final String SSO_STATE_SCOPE = "sso:state";
    private static final Duration SSO_STATE_TTL = Duration.ofMinutes(5);

    private final AuthSourceService authSourceService;
    private final AuthenticatorDispatcher authenticatorDispatcher;
    private final OneShotTokenService tokenService;

    /** LDAP / Credential 类登录:source 派发 + authenticator 校验 + 返 token。 */
    public AuthService.AuthResult loginByCredentialSource(Long sourceId, String username, String password) {
        AuthSource source = authSourceService.requireEnabled(sourceId);
        CredentialAuthenticator authenticator = authenticatorDispatcher.requireCredential(source.getType());
        return authenticator.authenticate(source, username, password);
    }

    /**
     * SSO 跳转入口:存入 state 防 CSRF,返回供 IdP 跳转的 authUrl。
     */
    public String buildSsoStartUrl(Long sourceId, String state) {
        AuthSource source = authSourceService.requireEnabled(sourceId);
        SsoAuthenticator authenticator = authenticatorDispatcher.requireSso(source.getType());
        tokenService.put(SSO_STATE_SCOPE, state, SSO_STATE_TTL);
        return authenticator.buildSignInUrl(source, state);
    }

    /** SSO 回调成功:校验 state,完成认证,返回成功跳转 URL。失败抛 BizException。 */
    public String handleSsoCallback(Long sourceId, String code, String state) {
        AuthSource source = authSourceService.requireEnabled(sourceId);
        SsoAuthenticator authenticator = authenticatorDispatcher.requireSso(source.getType());
        if (!tokenService.consume(SSO_STATE_SCOPE, state)) {
            throw new BizException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }
        AuthService.AuthResult result = authenticator.handleCallback(source, code, state);
        return authenticator.buildSuccessRedirect(source, result.token()).toString();
    }

    /**
     * SSO 失败回退跳转。如果 source 都无法解析(id 错误等),回退到平台根路径只带 errorCode。
     */
    public String buildSsoFailureRedirect(Long sourceId, String errorCode) {
        try {
            AuthSource source = authSourceService.requireEnabled(sourceId);
            SsoAuthenticator authenticator = authenticatorDispatcher.requireSso(source.getType());
            return authenticator.buildFailureRedirect(source, errorCode).toString();
        } catch (Exception e) {
            return "/?error=" + errorCode;
        }
    }
}
