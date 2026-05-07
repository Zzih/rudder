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

package io.github.zzih.rudder.service.config;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 启动期校验关键密钥/密码配置已设置且不是 well-known 弱值。Server 和 Execution 都装载,
 * 任何缺漏在 PostConstruct 阶段直接 fail-fast,避免运行时空指针/弱密钥风险。
 */
@Component
public class SecurityConfigValidator {

    private static final int MIN_SECRET_LENGTH = 32;

    private static final Set<String> INSECURE_JWT_SECRETS = Set.of(
            "123456",
            "key",
            "defaultJwtSecret",
            "secret");

    private static final Set<String> INSECURE_ENCRYPT_KEYS = Set.of(
            "123456",
            "key",
            "defaultEncryptionKey",
            "secret");

    @Value("${rudder.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${rudder.security.encrypt-key:}")
    private String encryptKey;

    @Value("${rudder.rpc.auth-secret:}")
    private String rpcAuthSecret;

    @PostConstruct
    void validate() {
        requireConfigured("rudder.security.jwt-secret", jwtSecret, INSECURE_JWT_SECRETS);
        requireConfigured("rudder.security.encrypt-key", encryptKey, INSECURE_ENCRYPT_KEYS);
        requireConfigured("rudder.rpc.auth-secret", rpcAuthSecret, INSECURE_JWT_SECRETS);
    }

    private static void requireConfigured(String name, String value, Set<String> insecureValues) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + name);
        }
        if (insecureValues.contains(value)) {
            throw new IllegalStateException(
                    "Insecure value for " + name + ": do not use well-known/default secrets");
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    name + " must be at least " + MIN_SECRET_LENGTH + " characters, got " + value.length());
        }
    }
}
