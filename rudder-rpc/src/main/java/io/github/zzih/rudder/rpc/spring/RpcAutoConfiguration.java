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

package io.github.zzih.rudder.rpc.spring;

import io.github.zzih.rudder.rpc.annotation.RpcService;
import io.github.zzih.rudder.rpc.client.RpcClient;
import io.github.zzih.rudder.rpc.server.RpcServer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * RPC 自动配置。创建 RpcServer 和 RpcClient Bean，并自动注册所有 @RpcService 实现。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RpcClient rpcClient(RpcProperties props) {
        return new RpcClient(props.getIoThreads(), props.getConnectTimeout(), props.getRequestTimeout(),
                props.getAuthSecret());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public RpcServer rpcServer(RpcProperties props, ApplicationContext ctx) {
        RpcServer server = new RpcServer(props.getPort(), props.getIoThreads(), props.getWorkerThreads(),
                props.getAuthSecret());

        // 扫描所有实现了 @RpcService 接口的 Spring Bean 并注册
        for (Object bean : ctx.getBeansWithAnnotation(org.springframework.stereotype.Component.class).values()) {
            for (Class<?> iface : bean.getClass().getInterfaces()) {
                if (iface.isAnnotationPresent(RpcService.class)) {
                    server.registerService(bean);
                    break;
                }
            }
        }
        return server;
    }
}
