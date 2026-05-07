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

package io.github.zzih.rudder.dao.enums;

/**
 * 认证源类型,落库为 {@link #name()},对应 {@code t_r_auth_source.type} 列。
 *
 * <p>新增类型 = 新协议接入(如 SAML / CAS),需同步:
 * <ol>
 *   <li>在 {@code rudder-service-server/.../auth/} 新增对应 {@code Authenticator} 实现</li>
 *   <li>登录页前端按 type 增加渲染分支</li>
 * </ol>
 */
public enum AuthSourceType {

    /** 本地账号(用户名 + BCrypt 密码),系统行,不可删/不可禁。 */
    PASSWORD,

    /** OpenID Connect / OAuth 2.0 授权码流程。 */
    OIDC,

    /** LDAP / Active Directory 认证。 */
    LDAP
}
