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

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * SPI provider 运行时上下文基类。
 *
 * <p>每个 SPI 家族（file/ai/version/...）基于此派生专属 context，
 * 在其中暴露该家族允许访问的宿主能力（DAO、Service）。
 *
 * <p>Provider 只能通过 context 访问宿主能力，禁止 {@code @Autowired} 任何宿主 bean。
 */
public interface ProviderContext {

    String decrypt(String ciphertext);

    String encrypt(String plaintext);

    ObjectMapper objectMapper();

    Clock clock();

    /** 用于 provider 打埋点（调用计数 / 延迟 / 错误率等）。 */
    MeterRegistry meterRegistry();
}
