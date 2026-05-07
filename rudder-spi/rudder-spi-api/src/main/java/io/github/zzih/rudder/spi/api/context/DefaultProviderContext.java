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

package io.github.zzih.rudder.spi.api.context;

import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;

import java.time.Clock;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 基础 {@link ProviderContext} 实现。
 *
 * <p>使用 AES 密钥做凭证加解密（由宿主通过构造参数注入），其余能力直接 passthrough。
 */
public class DefaultProviderContext implements ProviderContext {

    private final String aesKey;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public DefaultProviderContext(
                                  String aesKey, ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        this.aesKey = Objects.requireNonNull(aesKey, "aesKey");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        return CryptoUtils.aesDecrypt(ciphertext, aesKey);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        return CryptoUtils.aesEncrypt(plaintext, aesKey);
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public MeterRegistry meterRegistry() {
        return meterRegistry;
    }
}
