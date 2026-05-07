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
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.config.LdapSourceConfig;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.net.URI;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

import lombok.extern.slf4j.Slf4j;

/**
 * LDAP / Active Directory 认证。流程:
 * <ol>
 *   <li>用 bindDn + bindPassword bind 服务账号(允许匿名 bind 时跳过)</li>
 *   <li>按 userSearchFilter 在 baseDn 下搜索目标用户,拿到 DN + 属性</li>
 *   <li>用用户 DN + 用户输入密码 bind 验证密码</li>
 *   <li>调用 {@link AuthService#findOrCreateSsoUser} 合并/创建 t_r_user 行</li>
 *   <li>签 JWT 返回</li>
 * </ol>
 */
@Slf4j
@Component
public class LdapAuthenticator extends AbstractAuthenticator implements CredentialAuthenticator {

    public LdapAuthenticator(AuthService authService) {
        super(authService);
    }

    @Override
    public AuthSourceType type() {
        return AuthSourceType.LDAP;
    }

    @Override
    public AuthService.AuthResult authenticate(AuthSource source, String username, String password) {
        LdapSourceConfig config = parseConfig(source, LdapSourceConfig.class);

        try (LDAPConnection conn = createConnection(config)) {
            adminBind(conn, config);

            SearchResultEntry userEntry = searchUser(conn, config, username);
            if (userEntry == null) {
                throw new NotFoundException(WorkspaceErrorCode.LDAP_USER_NOT_FOUND);
            }

            verifyPassword(conn, userEntry.getDN(), password);

            String ldapUsername = userEntry.getAttributeValue(config.getUsernameAttribute());
            String email = userEntry.getAttributeValue(config.getEmailAttribute());
            if (ldapUsername == null || ldapUsername.isBlank()) {
                ldapUsername = username;
            }

            User user = authService.findOrCreateSsoUser(type().name(), userEntry.getDN(),
                    ldapUsername, email, null);
            return authService.generateTokenForUser(user);

        } catch (BizException e) {
            throw e;
        } catch (LDAPException | GeneralSecurityException e) {
            log.error("LDAP authentication failed for user: {}", username, e);
            throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
        }
    }

    @Override
    public HealthStatus testConnection(AuthSource source) {
        LdapSourceConfig config;
        try {
            config = parseConfig(source, LdapSourceConfig.class);
        } catch (BizException e) {
            return HealthStatus.unhealthy("config invalid");
        }
        try (LDAPConnection conn = createConnection(config)) {
            adminBind(conn, config);
            return HealthStatus.healthy();
        } catch (LDAPException | GeneralSecurityException e) {
            return HealthStatus.unhealthy(e.getMessage());
        } catch (Exception e) {
            return HealthStatus.unhealthy(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void adminBind(LDAPConnection conn, LdapSourceConfig config) throws LDAPException {
        if (config.getBindDn() != null && !config.getBindDn().isBlank()) {
            BindResult result = conn.bind(config.getBindDn(), config.getBindPassword());
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new AuthException(WorkspaceErrorCode.SSO_AUTH_FAILED);
            }
        }
    }

    private SearchResultEntry searchUser(LDAPConnection conn, LdapSourceConfig config,
                                         String username) throws LDAPException {
        String filterStr = config.getUserSearchFilter().replace("{0}", Filter.encodeValue(username));
        SearchRequest request = new SearchRequest(
                config.getBaseDn(),
                SearchScope.SUB,
                filterStr,
                config.getUsernameAttribute(),
                config.getEmailAttribute(),
                config.getDisplayNameAttribute());
        request.setSizeLimit(1);
        SearchResult result = conn.search(request);
        return result.getEntryCount() > 0 ? result.getSearchEntries().getFirst() : null;
    }

    private void verifyPassword(LDAPConnection conn, String userDn, String password) throws LDAPException {
        BindResult result = conn.bind(userDn, password);
        if (result.getResultCode() != ResultCode.SUCCESS) {
            throw new AuthException(WorkspaceErrorCode.PASSWORD_ERROR);
        }
    }

    private LDAPConnection createConnection(LdapSourceConfig config) throws LDAPException, GeneralSecurityException {
        URI uri = URI.create(config.getUrl());
        String host = uri.getHost();
        int port = uri.getPort();
        boolean useSsl = "ldaps".equalsIgnoreCase(uri.getScheme());

        if (port == -1) {
            port = useSsl ? 636 : 389;
        }

        if (useSsl) {
            SSLUtil sslUtil;
            if (config.isTrustAllCerts()) {
                log.warn("LDAP TLS certificate validation is disabled — not safe for production");
                sslUtil = new SSLUtil(new TrustAllTrustManager());
            } else {
                sslUtil = new SSLUtil();
            }
            SSLSocketFactory socketFactory = sslUtil.createSSLSocketFactory();
            return new LDAPConnection(socketFactory, host, port);
        }
        return new LDAPConnection(host, port);
    }
}
