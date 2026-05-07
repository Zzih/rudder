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

package io.github.zzih.rudder.service.coordination;

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨节点广播管道。封装 Redis pub/sub 的 channel 订阅 + 消息序列化 + nodeId 过滤,
 * 让上层协议(缓存失效、signal 唤醒等)不重复写 boilerplate。
 *
 * <h2>使用</h2>
 * <pre>{@code
 * // 1. 定义事件类型(record 实现 BroadcastEvent)
 * record MyEvent(String key, String payload, String originNodeId) implements BroadcastEvent {}
 *
 * // 2. 构造 broadcaster
 * RedisBroadcaster<MyEvent> broadcaster = new RedisBroadcaster<>(
 *         RedisNaming.Channels.MY_CHANNEL, nodeId, redis, container, MyEvent.class);
 *
 * // 3. 订阅(handler 只收到非本节点发的消息)
 * broadcaster.subscribe(event -> handle(event));
 *
 * // 4. 发广播
 * broadcaster.send(new MyEvent("foo", "bar", nodeId));
 * }</pre>
 *
 * <h2>不负责什么</h2>
 *
 * <ul>
 *   <li>不存数据 — 只是消息管道</li>
 *   <li>不保证投递 — Redis pub/sub 是 fire-and-forget</li>
 *   <li>不缓冲 — 订阅前发的消息丢失</li>
 * </ul>
 *
 * 上层协议(GlobalCacheService / PubSubSignalRegistry)各自处理"丢消息兜底"
 * (Caffeine TTL / L2 TTL 等)。
 *
 * @param <E> 事件类型,需实现 {@link BroadcastEvent}
 */
@Slf4j
public class RedisBroadcaster<E extends BroadcastEvent> {

    private final String channel;
    private final String nodeId;
    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer container;
    private final Class<E> eventType;

    public RedisBroadcaster(String channel, String nodeId,
                            StringRedisTemplate redis,
                            RedisMessageListenerContainer container,
                            Class<E> eventType) {
        this.channel = channel;
        this.nodeId = nodeId;
        this.redis = redis;
        this.container = container;
        this.eventType = eventType;
    }

    /**
     * 订阅。handler 只会收到 {@code originNodeId != 本节点 nodeId} 的事件
     * (本节点自己 send 的不会回到自己 handler)。
     */
    public void subscribe(Consumer<E> handler) {
        container.addMessageListener(new InternalListener(handler), new ChannelTopic(channel));
    }

    /** 发广播。事件的 {@link BroadcastEvent#originNodeId()} 应当是本节点 nodeId。 */
    public void send(E event) {
        try {
            redis.convertAndSend(channel, JsonUtils.toJson(event));
        } catch (Exception e) {
            log.warn("broadcast send failed channel={}: {}", channel, e.getMessage());
        }
    }

    /** 本节点 ID。业务方构造 event 时需要把它塞进 originNodeId。 */
    public String nodeId() {
        return nodeId;
    }

    private final class InternalListener implements MessageListener {

        private final Consumer<E> handler;

        InternalListener(Consumer<E> handler) {
            this.handler = handler;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                E event = JsonUtils.fromJson(body, eventType);
                if (event == null) {
                    return;
                }
                if (nodeId.equals(event.originNodeId())) {
                    // 自己发的自己收到,忽略 — 发送方应在 send 之前同步处理本地副作用
                    return;
                }
                handler.accept(event);
            } catch (Exception e) {
                log.warn("broadcast onMessage failed channel={}: {}", channel, e.getMessage());
            }
        }
    }
}
