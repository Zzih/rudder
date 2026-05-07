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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.rpc.protocol.*;

import java.util.concurrent.ExecutorService;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 服务端消息处理器。接收请求，查找对应的 MethodInvoker，在线程池中执行并返回响应。
 */
@Slf4j
@ChannelHandler.Sharable
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final MethodRegistry registry;
    private final ExecutorService executor;
    private final String authSecret;

    public RpcServerHandler(MethodRegistry registry, ExecutorService executor, String authSecret) {
        this.registry = registry;
        this.executor = executor;
        this.authSecret = authSecret;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcHeader header = msg.getHeader();
        if (!header.isRequest()) {
            return;
        }

        // 鉴权：校验 HMAC 签名 + 时间戳偏移
        if (!RpcAuth.verify(authSecret, header.getMethodId(), header.getOpaque(),
                header.getTimestamp(), header.getSignature())) {
            log.warn("Rejecting RPC request with invalid signature: method={}, opaque={}, remote={}",
                    header.getMethodId(), header.getOpaque(), ctx.channel().remoteAddress());
            RpcHeader respHeader = RpcHeader.response(header.getOpaque());
            byte[] respBody = JsonUtils.toJson(RpcResponse.fail("RPC authentication failed")).getBytes();
            ctx.writeAndFlush(new RpcMessage(respHeader, respBody));
            return;
        }

        executor.submit(() -> {
            RpcResponse response;
            try {
                MethodInvoker invoker = registry.get(header.getMethodId());
                if (invoker == null) {
                    response = RpcResponse.fail("Method not found: " + header.getMethodId());
                } else {
                    // 反序列化参数
                    RpcRequest request = JsonUtils.fromJson(new String(msg.getBody()), RpcRequest.class);
                    Object[] args = deserializeArgs(request, invoker.getParameterTypes());

                    // 调用方法
                    Object result = invoker.invoke(args);

                    // 序列化返回值
                    if (result == null || invoker.getReturnType() == void.class) {
                        response = RpcResponse.ok();
                    } else {
                        byte[] body = JsonUtils.toJson(result).getBytes();
                        response = RpcResponse.ok(body, invoker.getReturnType().getName());
                    }
                }
            } catch (Exception e) {
                log.error("RPC method invocation failed: {}", header.getMethodId(), e);
                String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                response = RpcResponse.fail(errorMsg);
            }

            // 发送响应
            RpcHeader respHeader = RpcHeader.response(header.getOpaque());
            byte[] respBody = JsonUtils.toJson(response).getBytes();
            ctx.writeAndFlush(new RpcMessage(respHeader, respBody));
        });
    }

    private Object[] deserializeArgs(RpcRequest request, Class<?>[] paramTypes) throws ClassNotFoundException {
        if (request.getArgs() == null || request.getArgs().length == 0) {
            return new Object[0];
        }
        Object[] args = new Object[request.getArgs().length];
        for (int i = 0; i < args.length; i++) {
            if (request.getArgs()[i] == null) {
                args[i] = null;
            } else {
                Class<?> type = paramTypes.length > i ? paramTypes[i] : Class.forName(request.getArgTypes()[i]);
                args[i] = JsonUtils.fromJson(new String(request.getArgs()[i]), type);
            }
        }
        return args;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("RPC server channel error: {}", cause.getMessage());
        ctx.close();
    }
}
