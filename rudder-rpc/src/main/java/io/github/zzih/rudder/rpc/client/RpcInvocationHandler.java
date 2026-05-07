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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.rpc.annotation.RpcMethod;
import io.github.zzih.rudder.rpc.protocol.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

/**
 * JDK 动态代理的 InvocationHandler。
 * 将接口方法调用转换为 RPC 请求，通过 RpcClient 发送到远程节点。
 */
@Slf4j
public class RpcInvocationHandler implements InvocationHandler {

    private final Class<?> serviceInterface;
    private final String host;
    private final RpcClient client;

    public RpcInvocationHandler(Class<?> serviceInterface, String host, RpcClient client) {
        this.serviceInterface = serviceInterface;
        this.host = host;
        this.client = client;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 方法直接处理
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // 非 @RpcMethod 的方法不走 RPC
        RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
        if (rpcMethod == null) {
            throw new UnsupportedOperationException("Method not annotated with @RpcMethod: " + method.getName());
        }

        // 构建方法标识符
        String methodId = serviceInterface.getName() + "#" + method.getName();

        // 序列化参数
        RpcRequest request = new RpcRequest();
        if (args != null && args.length > 0) {
            byte[][] serializedArgs = new byte[args.length][];
            String[] argTypes = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    serializedArgs[i] = JsonUtils.toJson(args[i]).getBytes();
                    argTypes[i] = args[i].getClass().getName();
                }
            }
            request.setArgs(serializedArgs);
            request.setArgTypes(argTypes);
        }

        // 构建消息并签名
        RpcHeader header = RpcHeader.request(methodId);
        long timestamp = System.currentTimeMillis();
        header.setTimestamp(timestamp);
        header.setSignature(RpcAuth.sign(client.getAuthSecret(), methodId, header.getOpaque(), timestamp));
        byte[] body = JsonUtils.toJson(request).getBytes();
        RpcMessage message = new RpcMessage(header, body);

        // 发送并等待响应
        RpcMessage respMsg = client.sendSync(host, message);
        RpcResponse response = JsonUtils.fromJson(new String(respMsg.getBody()), RpcResponse.class);

        if (!response.isSuccess()) {
            throw new RuntimeException("RPC call failed [" + methodId + " → " + host + "]: " + response.getError());
        }

        // 反序列化返回值
        if (response.getBody() == null || method.getReturnType() == void.class) {
            return null;
        }
        return JsonUtils.fromJson(new String(response.getBody()), method.getReturnType());
    }
}
