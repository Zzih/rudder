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

import io.github.zzih.rudder.service.coordination.RedisNaming;
import io.github.zzih.rudder.service.coordination.signal.PubSubSignalRegistry;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

/**
 * 流(turn)取消注册表。基于 {@link PubSubSignalRegistry} 实现跨节点 cancel。
 *
 * <p>存的是 {@link CancellationHandle}(含 reactor {@code Disposable} + 绑定线程),
 * 这些是 JVM-bound 对象只能留在发起订阅的节点。跨节点 cancel 时先本地查,
 * miss 就通过 Redis pub/sub 把 streamId 广播给其他节点,由持有 handle 的那个节点
 * 在本地执行 {@code dispose()}。
 */
@Slf4j
public class StreamRegistry extends PubSubSignalRegistry<CancellationHandle> {

    public static final String CHANNEL = RedisNaming.Channels.STREAM_CANCEL;

    /** 流通常几十秒结束,30min 后本地 handle 自动清理防泄漏。 */
    public static final Duration HANDLE_TTL = Duration.ofMinutes(30);

    private static final String PAYLOAD_CANCEL = "CANCEL";

    public StreamRegistry(String nodeId,
                          StringRedisTemplate redis,
                          RedisMessageListenerContainer container) {
        super(nodeId, CHANNEL, redis, container, HANDLE_TTL);
    }

    /** 注册新流,返回取消句柄。 */
    public CancellationHandle register(long messageId, long sessionId) {
        String streamId = UUID.randomUUID().toString();
        CancellationHandle handle = new CancellationHandle(streamId, messageId, sessionId, nodeId);
        registerLocal(streamId, handle);
        return handle;
    }

    /** 请求取消指定 streamId。先本地,miss 走 pub/sub 广播。 */
    public boolean cancel(String streamId) {
        return signal(streamId, PAYLOAD_CANCEL);
    }

    @Override
    protected void applyLocal(CancellationHandle handle, String payload) {
        boolean first = handle.doCancel();
        if (first) {
            log.info("stream {} cancelled on node {}", handle.getStreamId(), nodeId);
        }
    }
}
