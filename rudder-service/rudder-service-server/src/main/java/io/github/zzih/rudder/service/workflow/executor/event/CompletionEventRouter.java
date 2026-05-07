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

package io.github.zzih.rudder.service.workflow.executor.event;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 单 runner 的任务完成事件队列 + 去重集合。
 *
 * <p>{@link #offer} 是线程安全的（RPC 回调 / 兜底轮询 / cancel sentinel 三方会并发调用），
 * {@link #poll} 只由 Runner 主循环线程调用。
 *
 * <p>去重 key = {@code taskInstanceId}：RPC 回调与轮询可能对同一完成事件重复触发，
 * 若不去重会在 {@code handleCompletionEvent} 里重复 merge varPool / 重复 updateById。
 * 取消 sentinel（taskCode 负值）绕过去重，保证 cancel 一定能进队列。
 */
public final class CompletionEventRouter {

    private final BlockingQueue<NodeCompletionEvent> queue = new LinkedBlockingQueue<>();
    private final Set<Long> notifiedTaskInstanceIds = ConcurrentHashMap.newKeySet();

    /** 尝试入队：相同 taskInstanceId 的完成事件只入一次；cancel sentinel（taskCode &lt; 0）不参与去重。 */
    public void offer(NodeCompletionEvent event) {
        if (event.getTaskCode() == null || event.getTaskCode() < 0) {
            queue.offer(event);
            return;
        }
        Long taskInstanceId = event.getTaskInstanceId();
        if (taskInstanceId != null && !notifiedTaskInstanceIds.add(taskInstanceId)) {
            return;
        }
        queue.offer(event);
    }

    /** Runner 主循环轮询用。{@code null} 表示超时。 */
    public NodeCompletionEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * HA 接管时登记已经处于终态的 task_instance id，避免后续 RPC 回调重复处理。
     * 只在 Runner 主线程构造期调用，天然单线程。
     */
    public void markNotified(Long taskInstanceId) {
        if (taskInstanceId != null) {
            notifiedTaskInstanceIds.add(taskInstanceId);
        }
    }
}
