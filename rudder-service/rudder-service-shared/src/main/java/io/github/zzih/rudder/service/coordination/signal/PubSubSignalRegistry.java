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

package io.github.zzih.rudder.service.coordination.signal;

import io.github.zzih.rudder.service.coordination.BroadcastEvent;
import io.github.zzih.rudder.service.coordination.RedisBroadcaster;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨节点信号注册表的通用基类。
 *
 * <p>解决的问题:Rudder 里存在多个场景需要**"在本节点等待外部信号(另一个 HTTP 请求),
 * 同时该请求可能打到另一个节点"**,已知两个:
 * <ul>
 *   <li>流取消:用户点停止 → 可能落到 Node-2,但 Flux 在 Node-1 持有</li>
 *   <li>工具审批:用户点批准 → 可能落到 Node-2,但 agent 线程在 Node-1 阻塞</li>
 * </ul>
 *
 * <p>本类封装统一的"**本地持有 handle + Redis pub/sub 广播信号**"模式:
 * <ol>
 *   <li>子类 register 时在本地 Caffeine 存一个 handle(类型 H 由子类定,必须是 JVM-bound
 *       不可序列化对象,比如 {@code CompletableFuture} / reactor {@code Disposable})</li>
 *   <li>{@code signal} 时先本地查 Caffeine,命中就直接 {@link #applyLocal} 当场生效</li>
 *   <li>本地没命中,发 Redis pub/sub 消息(走 {@link RedisBroadcaster}),其他节点的订阅回调回到**自己的**
 *       Caffeine 找,找到再 applyLocal</li>
 * </ol>
 *
 * <p>子类只需实现 {@link #applyLocal}(拿到 handle 和 payload 后干啥),通常就一行:
 * <pre>
 *   // StreamRegistry:           handle.doCancel()
 *   // ToolApprovalRegistry:     future.complete(Boolean.parseBoolean(payload))
 * </pre>
 *
 * <p>延迟:同节点 ~μs,跨节点 Redis pub/sub ~5-50ms。远比纯轮询(1s 级)好。
 *
 * @param <H> 本地 handle 类型
 */
@Slf4j
public abstract class PubSubSignalRegistry<H> {

    protected final String nodeId;
    protected final Cache<String, H> local;
    private final RedisBroadcaster<SignalEvent> broadcaster;

    /**
     * @param nodeId 本节点唯一标识(用来自己广播的消息自己收到时忽略)
     * @param channel Redis pub/sub 频道名
     * @param redis StringRedisTemplate
     * @param container RedisMessageListenerContainer(Spring 装配共享)
     * @param handleTtl 本地 Caffeine TTL(handle 长期没被 signal 自动清,防泄漏)
     */
    protected PubSubSignalRegistry(
                                   String nodeId,
                                   String channel,
                                   StringRedisTemplate redis,
                                   RedisMessageListenerContainer container,
                                   Duration handleTtl) {
        this.nodeId = nodeId;
        this.local = Caffeine.newBuilder()
                .expireAfterWrite(handleTtl)
                .maximumSize(10_000)
                .build();
        this.broadcaster = new RedisBroadcaster<>(channel, nodeId, redis, container, SignalEvent.class);
        this.broadcaster.subscribe(this::onRemoteSignal);
    }

    // ==================== 子类 API ====================

    /** 注册一个本地 handle。通常在 agent 发起等待时调用。 */
    protected void registerLocal(String key, H handle) {
        local.put(key, handle);
    }

    /** 清理本地 handle。通常在 finally 里调。 */
    public void unregister(String key) {
        local.invalidate(key);
    }

    /** 本节点是否持有该 key。 */
    public H find(String key) {
        return local.getIfPresent(key);
    }

    /**
     * 触发信号。先本地 applyLocal,本地没命中就 Redis pub/sub 广播。
     *
     * @return 至少已被尝试(本地命中 / 广播成功)。不代表目标 handle 真的在任何节点存在。
     */
    public boolean signal(String key, String payload) {
        H handle = local.getIfPresent(key);
        if (handle != null) {
            applyLocal(handle, payload);
            return true;
        }
        broadcaster.send(new SignalEvent(key, payload, nodeId));
        return true;
    }

    /**
     * 对本地 handle 应用 payload。子类实现,通常一行。
     *
     * <p>**应当幂等**。同节点 signal 同步直接调,跨节点经 {@link RedisBroadcaster} 过滤,
     * 正常路径不会重复触发,但子类写成幂等更稳。
     */
    protected abstract void applyLocal(H handle, String payload);

    // ==================== 内部:远端事件处理 ====================

    /** 远端节点发来的信号(RedisBroadcaster 已过滤掉自己发的)。 */
    private void onRemoteSignal(SignalEvent event) {
        if (event.key() == null) {
            return;
        }
        H handle = local.getIfPresent(event.key());
        if (handle != null) {
            applyLocal(handle, event.payload());
        }
        // 其他节点的信号本节点没 handle,正常,那个 handle 在别的节点(或已过期)
    }

    /** pub/sub 消息体。需要公开,Jackson 反序列化能访问。 */
    public record SignalEvent(String key, String payload, String originNodeId) implements BroadcastEvent {
    }
}
