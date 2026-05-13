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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.SslOptions;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/** 跳过 Redis TLS 证书校验,失去 MITM 防护;优先用 spring.data.redis.ssl.bundle 自带 truststore。 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis.ssl", name = "insecure", havingValue = "true")
public class RedisSslInsecureConfig {

    @PostConstruct
    void logInsecureMode() {
        log.warn("Redis TLS 证书校验已关闭(spring.data.redis.ssl.insecure=true),失去 MITM 防护。建议改用 spring.data.redis.ssl.bundle 引用自带 truststore 的 SSL Bundle。");
    }

    @Bean
    public LettuceClientOptionsBuilderCustomizer redisInsecureSslCustomizer() {
        return builder -> builder.sslOptions(SslOptions.builder()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build());
    }
}
