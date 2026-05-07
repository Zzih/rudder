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
 * LDAP source 的配置 POJO,跟 {@code t_r_auth_source.config_json}(type=LDAP) 一一对应。
 *
 * <p>{@link #bindPassword} 在落库前由 service 层 AES 加密;读出后由 service 层解密再传给 Authenticator。
 */
@Data
public final class LdapSourceConfig implements SourceConfig {

    /** LDAP 服务器地址。例: {@code ldap://ad.company.com:389} 或 {@code ldaps://ad.company.com:636}。 */
    @NotBlank
    private String url;

    /** 是否信任所有 TLS 证书(仅开发/测试,生产必须 false)。 */
    private boolean trustAllCerts = false;

    /** 搜索用户的根 DN。例: {@code dc=company,dc=com}。 */
    @NotBlank
    private String baseDn;

    /** 用于绑定(连接)LDAP 的服务账号 DN。允许匿名 bind 时可留空。 */
    private String bindDn;

    private String bindPassword;

    /**
     * 用户搜索过滤器,{@code {0}} 替换为用户输入的用户名。
     * AD: {@code (&(objectClass=user)(sAMAccountName={0}))}
     * OpenLDAP: {@code (&(objectClass=inetOrgPerson)(uid={0}))}
     */
    @NotBlank
    private String userSearchFilter = "(&(objectClass=user)(sAMAccountName={0}))";

    /** 用户名属性名(AD: {@code sAMAccountName}; OpenLDAP: {@code uid})。 */
    @NotBlank
    private String usernameAttribute = "sAMAccountName";

    /** 邮箱属性名。 */
    private String emailAttribute = "mail";

    /** 显示名属性名。 */
    private String displayNameAttribute = "displayName";
}
