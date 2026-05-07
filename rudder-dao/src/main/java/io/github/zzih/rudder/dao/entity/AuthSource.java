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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.entity.BaseEntity;
import io.github.zzih.rudder.dao.enums.AuthSourceType;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 认证源(登录方式)。每行 = 一种登录方式 + 它的配置参数,跟用户表 {@code t_r_user} 互不嵌套。
 *
 * <p>用户登录命中某个 source → 对应 {@code Authenticator} 处理外部认证 →
 * 最终落到 {@code t_r_user} 一行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_auth_source")
public class AuthSource extends BaseEntity {

    /** 显示名(登录页按钮文案 / admin 列表识别),不可重。 */
    private String name;

    /** 登录方式类型。 */
    private AuthSourceType type;

    /** 是否启用。仅 {@code is_system=1} 的行强制启用,其余可启停。 */
    private Boolean enabled;

    /** 系统行(=1)不可删/不可禁,目前仅默认 PASSWORD 行=1。 */
    private Boolean isSystem;

    /** 登录页按钮排序,值大者靠前。 */
    private Integer priority;

    /**
     * 协议特定配置 JSON。OIDC: client_id/secret/endpoint 等; LDAP: url/baseDn/bind 等。
     * 敏感字段(client_secret / bind_password)走 {@code RUDDER_ENCRYPT_KEY} AES 加密。
     * PASSWORD 行无配置,允许 NULL。
     */
    private String configJson;
}
