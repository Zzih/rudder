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

package io.github.zzih.rudder.rpc.client;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 客户端入口。通过 JDK 动态代理创建远程服务的本地代理。
 * <pre>
 * ITaskExecutionService service = Clients.create(rpcClient, ITaskExecutionService.class, "execution-host:5691");
 * service.dispatch(request);
 * </pre>
 */
public final class Clients {

    /** 代理缓存：host + interfaceName → proxy */
    private static final Map<String, Object> PROXY_CACHE = new ConcurrentHashMap<>();

    private Clients() {
    }

    /**
     * 创建远程服务代理。相同 host + 接口 会复用缓存的代理实例。
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(RpcClient client, Class<T> serviceInterface, String host) {
        String key = host + ":" + serviceInterface.getName();
        return (T) PROXY_CACHE.computeIfAbsent(key, k -> Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new RpcInvocationHandler(serviceInterface, host, client)));
    }
}
