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

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "rudder.rpc")
public class RpcProperties {

    /** RPC 服务端口（必须在 application.yml 中配置 rudder.rpc.port） */
    private int port;

    /** Netty IO 线程数 */
    private int ioThreads = 4;

    /** 业务处理线程数 */
    private int workerThreads = 8;

    /** 客户端连接超时（ms） */
    private int connectTimeout = 5000;

    /** 客户端请求超时（ms） */
    private long requestTimeout = 10000;

    /**
     * RPC 鉴权共享密钥。Server 和 Execution 必须使用相同值。
     * 必填；若留空则启动失败（通过 {@code SecurityConfigValidator}）。
     */
    private String authSecret;
}
