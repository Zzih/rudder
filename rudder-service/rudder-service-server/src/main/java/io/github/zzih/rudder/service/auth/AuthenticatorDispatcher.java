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
import io.github.zzih.rudder.dao.enums.AuthSourceType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 按 {@link AuthSourceType} 派发到对应 {@link Authenticator} 的注册表。Controller / Service 注入此 bean。
 *
 * <p>启动时收集所有 {@code @Component} 标注的 {@link Authenticator},按 {@link Authenticator#type()} 入 map。
 * 同 type 多实现会启动失败(避免歧义)。
 */
@Component
public class AuthenticatorDispatcher {

    private final Map<AuthSourceType, Authenticator> registry;

    public AuthenticatorDispatcher(List<Authenticator> all) {
        EnumMap<AuthSourceType, Authenticator> map = new EnumMap<>(AuthSourceType.class);
        for (Authenticator a : all) {
            Authenticator existing = map.put(a.type(), a);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate Authenticator for type " + a.type()
                                + ": " + existing.getClass() + " vs " + a.getClass());
            }
        }
        this.registry = Map.copyOf(map);
    }

    /** 返回对应 type 的 Authenticator,缺失抛 {@link BizException}。 */
    public Authenticator require(AuthSourceType type) {
        Authenticator a = registry.get(type);
        if (a == null) {
            throw new BizException(WorkspaceErrorCode.SSO_PROVIDER_NOT_SUPPORTED);
        }
        return a;
    }

    /** 要求按 type 找到的 Authenticator 同时是 {@link CredentialAuthenticator}。 */
    public CredentialAuthenticator requireCredential(AuthSourceType type) {
        Authenticator a = require(type);
        if (a instanceof CredentialAuthenticator c) {
            return c;
        }
        throw new BizException(WorkspaceErrorCode.SSO_PROVIDER_NOT_SUPPORTED);
    }

    /** 要求按 type 找到的 Authenticator 同时是 {@link SsoAuthenticator}。 */
    public SsoAuthenticator requireSso(AuthSourceType type) {
        Authenticator a = require(type);
        if (a instanceof SsoAuthenticator s) {
            return s;
        }
        throw new BizException(WorkspaceErrorCode.SSO_PROVIDER_NOT_SUPPORTED);
    }
}
