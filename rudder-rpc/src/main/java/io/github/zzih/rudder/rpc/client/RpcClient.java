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

import io.github.zzih.rudder.rpc.protocol.RpcDecoder;
import io.github.zzih.rudder.rpc.protocol.RpcEncoder;
import io.github.zzih.rudder.rpc.protocol.RpcMessage;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 客户端。管理到远程节点的 Netty Channel 连接池。
 */
@Slf4j
public class RpcClient {

    private final int connectTimeoutMs;
    private final long requestTimeoutMs;
    private final String authSecret;
    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final RpcClientHandler handler = new RpcClientHandler();

    public RpcClient(int ioThreads, int connectTimeoutMs, long requestTimeoutMs, String authSecret) {
        if (authSecret == null || authSecret.isBlank()) {
            throw new IllegalArgumentException("RPC auth secret is required (rudder.rpc.auth-secret)");
        }
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.authSecret = authSecret;
        this.group = new NioEventLoopGroup(ioThreads);
        this.bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new RpcDecoder())
                                .addLast(new RpcEncoder())
                                .addLast(handler);
                    }
                });
    }

    /**
     * 同步发送 RPC 请求并等待响应。
     */
    public RpcMessage sendSync(String host, RpcMessage request) throws Exception {
        Channel channel = getOrCreateChannel(host);
        long opaque = request.getHeader().getOpaque();
        long timeout = requestTimeoutMs;

        RpcFuture future = new RpcFuture(opaque, timeout);
        channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                RpcFuture.FUTURES.remove(opaque);
                future.complete(null);
                log.error("Failed to send RPC request to {}: {}", host, f.cause().getMessage());
            }
        });

        RpcMessage response = future.get();
        if (response == null) {
            throw new TimeoutException("RPC request failed to " + host);
        }
        return response;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    private Channel getOrCreateChannel(String host) throws Exception {
        Channel channel = channels.get(host);
        if (channel != null && channel.isActive()) {
            return channel;
        }

        synchronized (this) {
            channel = channels.get(host);
            if (channel != null && channel.isActive()) {
                return channel;
            }

            String[] parts = host.split(":");
            InetSocketAddress address = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
            channel = bootstrap.connect(address).sync().channel();
            channels.put(host, channel);
            log.debug("RPC channel created to {}", host);

            // 连接断开时自动移除
            channel.closeFuture().addListener(f -> {
                channels.remove(host);
                log.debug("RPC channel closed to {}", host);
            });

            return channel;
        }
    }

    public void shutdown() {
        channels.values().forEach(Channel::close);
        channels.clear();
        group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        log.info("RPC client shutdown");
    }
}
