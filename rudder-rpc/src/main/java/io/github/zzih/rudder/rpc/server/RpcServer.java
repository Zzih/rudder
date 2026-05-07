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

import io.github.zzih.rudder.rpc.protocol.RpcDecoder;
import io.github.zzih.rudder.rpc.protocol.RpcEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 服务端。在指定端口启动 Netty 服务，接收并处理 RPC 请求。
 */
@Slf4j
public class RpcServer {

    private final int port;
    private final int ioThreads;
    private final int workerThreads;
    private final String authSecret;
    private final MethodRegistry registry = new MethodRegistry();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService bizExecutor;
    private Channel serverChannel;

    public RpcServer(int port, int ioThreads, int workerThreads, String authSecret) {
        if (authSecret == null || authSecret.isBlank()) {
            throw new IllegalArgumentException("RPC auth secret is required (rudder.rpc.auth-secret)");
        }
        this.port = port;
        this.ioThreads = ioThreads;
        this.workerThreads = workerThreads;
        this.authSecret = authSecret;
    }

    /** 注册 RPC 服务实现 Bean */
    public void registerService(Object serviceBean) {
        registry.register(serviceBean);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(ioThreads);
        bizExecutor = new ThreadPoolExecutor(
                workerThreads, workerThreads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10000),
                r -> new Thread(r, "rpc-biz-" + r.hashCode()),
                new ThreadPoolExecutor.CallerRunsPolicy());

        RpcServerHandler handler = new RpcServerHandler(registry, bizExecutor, authSecret);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS))
                                .addLast(new RpcDecoder())
                                .addLast(new RpcEncoder())
                                .addLast(handler);
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("RPC server started on port {}, {} methods registered", port, registry.size());
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bizExecutor != null) {
            bizExecutor.shutdownNow();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("RPC server stopped");
    }
}
