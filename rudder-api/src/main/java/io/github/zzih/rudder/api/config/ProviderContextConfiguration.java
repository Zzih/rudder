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

package io.github.zzih.rudder.api.config;

import io.github.zzih.rudder.spi.api.context.DefaultProviderContext;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 组装 SPI provider 运行时上下文。宿主唯一知道 DAO/Service 的地方；从这里以后，
 * 各 provider 只看到 {@link ProviderContext} 及其派生接口，不得 {@code @Autowired} 任何宿主 bean。
 */
@Configuration
public class ProviderContextConfiguration {

    @Bean
    @Primary
    public ProviderContext providerContext(
                                           @Value("${rudder.security.encrypt-key:}") String aesKey,
                                           ObjectMapper objectMapper,
                                           org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistry) {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException(
                    "rudder.security.encrypt-key must be configured for provider encryption");
        }
        MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
        return new DefaultProviderContext(aesKey, objectMapper, Clock.systemUTC(), registry);
    }

}
