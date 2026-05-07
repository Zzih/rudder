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

package io.github.zzih.rudder.ai.orchestrator.tool;

import io.github.zzih.rudder.service.coordination.NodeIdProvider;
import io.github.zzih.rudder.service.coordination.RedisNaming;
import io.github.zzih.rudder.service.coordination.signal.PubSubSignalRegistry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 写类工具审批等待站。基于 {@link PubSubSignalRegistry}:
 *
 * <ul>
 *   <li>本地存 {@link CompletableFuture} 让 agent 线程阻塞等结果</li>
 *   <li>前端审批接口可能打到另一节点 → 走 Redis pub/sub 广播,持有 future 的节点收到信号后 complete</li>
 * </ul>
 *
 * <p>延迟:同节点 ~μs(future.complete 直接唤醒),跨节点 ~10-50ms(pub/sub)。
 * Agent 外层每秒检查一次 {@code handle.isCancelled()},所以最大看到的延迟仍是 1s,但
 * 绝大多数批准在 future.get() 即时返回。
 */
@Slf4j
@Component
public class ToolApprovalRegistry extends PubSubSignalRegistry<CompletableFuture<Boolean>> {

    /** Agent 等待超时。超时后抛 {@link ToolRejectedException}。 */
    public static final Duration TIMEOUT = Duration.ofMinutes(5);

    public static final String CHANNEL = RedisNaming.Channels.TOOL_APPROVAL;

    /** 本地 future TTL:业务超时 + 1min 兜底,防 agent 异常未清理。 */
    private static final Duration HANDLE_TTL = TIMEOUT.plusMinutes(1);

    private static final String PAYLOAD_APPROVED = "true";
    private static final String PAYLOAD_REJECTED = "false";

    public ToolApprovalRegistry(NodeIdProvider nodeIdProvider,
                                StringRedisTemplate redis,
                                RedisMessageListenerContainer container) {
        super(nodeIdProvider.nodeId(), CHANNEL, redis, container, HANDLE_TTL);
    }

    /** Agent 线程调用:注册一个 future 并阻塞等结果。 */
    public CompletableFuture<Boolean> register(String streamId, String toolCallId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        registerLocal(key(streamId, toolCallId), future);
        return future;
    }

    /**
     * 前端审批接口调用。先本地 future.complete(同节点同步生效);miss 广播给其他节点。
     *
     * @return 至少已尝试(本地命中 / 广播成功)。不保证目标 future 真存在于任何节点
     *         (可能超时已清,或 agent 进程崩)。
     */
    public boolean complete(String streamId, String toolCallId, boolean approved) {
        return signal(key(streamId, toolCallId), approved ? PAYLOAD_APPROVED : PAYLOAD_REJECTED);
    }

    public void unregister(String streamId, String toolCallId) {
        super.unregister(key(streamId, toolCallId));
    }

    @Override
    protected void applyLocal(CompletableFuture<Boolean> future, String payload) {
        // complete 幂等,重复 complete 返回 false,不影响正确性
        future.complete(PAYLOAD_APPROVED.equals(payload));
    }

    private static String key(String streamId, String toolCallId) {
        return streamId + "|" + toolCallId;
    }
}
