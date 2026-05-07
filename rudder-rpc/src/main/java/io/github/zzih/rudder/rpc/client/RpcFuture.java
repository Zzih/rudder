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

import io.github.zzih.rudder.rpc.protocol.RpcMessage;

import java.util.Map;
import java.util.concurrent.*;

/**
 * RPC 请求的异步结果容器。通过 opaque ID 匹配请求和响应。
 */
public class RpcFuture {

    /** 全局注册表：opaque → future */
    static final Map<Long, RpcFuture> FUTURES = new ConcurrentHashMap<>();

    private final long opaque;
    private final long timeoutMs;
    private final BlockingQueue<RpcMessage> queue = new ArrayBlockingQueue<>(1);

    public RpcFuture(long opaque, long timeoutMs) {
        this.opaque = opaque;
        this.timeoutMs = timeoutMs;
        FUTURES.put(opaque, this);
    }

    /** 阻塞等待响应 */
    public RpcMessage get() throws TimeoutException, InterruptedException {
        try {
            RpcMessage msg = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (msg == null) {
                throw new TimeoutException("RPC request timed out after " + timeoutMs + "ms, opaque=" + opaque);
            }
            return msg;
        } finally {
            FUTURES.remove(opaque);
        }
    }

    /** 由 RpcClientHandler 调用，设置响应并唤醒等待线程 */
    public void complete(RpcMessage response) {
        queue.offer(response);
    }

    /** 根据 opaque 查找并完成 future */
    public static void resolve(long opaque, RpcMessage response) {
        RpcFuture future = FUTURES.remove(opaque);
        if (future != null) {
            future.complete(response);
        }
    }
}
