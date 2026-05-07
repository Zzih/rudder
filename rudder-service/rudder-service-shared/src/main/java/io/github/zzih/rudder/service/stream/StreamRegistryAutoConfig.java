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

package io.github.zzih.rudder.service.stream;

import io.github.zzih.rudder.service.coordination.NodeIdProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

/**
 * StreamRegistry 自动装配。Redis 是平台强依赖,不再做单节点降级。
 */
@Slf4j
@Configuration
public class StreamRegistryAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public StreamRegistry streamRegistry(NodeIdProvider nodeIdProvider,
                                         StringRedisTemplate redisTemplate,
                                         RedisMessageListenerContainer container) {
        String nodeId = nodeIdProvider.nodeId();
        log.info("StreamRegistry ready, nodeId={}", nodeId);
        return new StreamRegistry(nodeId, redisTemplate, container);
    }

    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
