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

package io.github.zzih.rudder.execution.config;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.spi.api.context.DefaultProviderContext;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * rudder-execution 侧的 SPI 上下文装配。Execution 侧只跑 file / runtime / task / result SPI,
 * 全部用 base {@link ProviderContext} 就够,不涉及 approval / version / metadata 的扩展能力。
 * <p>
 * Execution 进程不引 spring-boot-starter-web,没有 Jackson 2 ObjectMapper 自动装配;
 * 复用 {@code JsonUtils.getObjectMapper()} 的共享实例(全项目 JSON 处理同一个)。
 */
@Configuration
public class ProviderContextConfiguration {

    @Bean
    @Primary
    public ProviderContext providerContext(
                                           @Value("${rudder.security.encrypt-key:}") String aesKey,
                                           org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistry) {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException(
                    "rudder.security.encrypt-key must be configured for provider encryption");
        }
        MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
        return new DefaultProviderContext(aesKey, JsonUtils.getObjectMapper(), Clock.systemUTC(), registry);
    }
}
