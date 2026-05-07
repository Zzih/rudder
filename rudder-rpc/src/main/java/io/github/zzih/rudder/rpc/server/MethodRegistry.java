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

package io.github.zzih.rudder.rpc.server;

import io.github.zzih.rudder.rpc.annotation.RpcMethod;
import io.github.zzih.rudder.rpc.annotation.RpcService;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * RPC 方法注册表。扫描 {@link RpcService} 接口的实现类，注册所有 {@link RpcMethod} 方法。
 * <p>
 * 方法标识符格式：{@code 接口全限定名#方法名}
 */
@Slf4j
public class MethodRegistry {

    private final Map<String, MethodInvoker> invokers = new ConcurrentHashMap<>();

    /**
     * 注册一个 RPC 服务实现。扫描其接口上的 @RpcService 和 @RpcMethod。
     */
    public void register(Object bean) {
        for (Class<?> iface : bean.getClass().getInterfaces()) {
            if (!iface.isAnnotationPresent(RpcService.class)) {
                continue;
            }

            for (Method method : iface.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(RpcMethod.class)) {
                    continue;
                }

                String methodId = iface.getName() + "#" + method.getName();
                invokers.put(methodId, new MethodInvoker(bean, method));
                log.info("Registered RPC method: {}", methodId);
            }
        }
    }

    public MethodInvoker get(String methodId) {
        return invokers.get(methodId);
    }

    public int size() {
        return invokers.size();
    }
}
