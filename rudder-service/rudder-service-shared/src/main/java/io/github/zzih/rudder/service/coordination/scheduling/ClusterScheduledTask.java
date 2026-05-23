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

package io.github.zzih.rudder.service.coordination.scheduling;

import java.time.Duration;

/**
 * 集群级单触发任务定义。
 *
 * @param key      全集群唯一 key,锁/状态/触发标记 都用此 key 派生
 * @param interval 上轮结束到下轮开始的间隔(非墙钟对齐)
 * @param lockTtl  锁过期 TTL,须 > 单轮预期最大执行时间;心跳每 {@code lockTtl/3} 续约一次
 * @param runnable 实际任务体,**实现必须幂等**(心跳失效被抢可能并发)
 */
public record ClusterScheduledTask(
        String key,
        Duration interval,
        Duration lockTtl,
        Runnable runnable) {

    public ClusterScheduledTask {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("ClusterScheduledTask.key must not be blank");
        }
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("ClusterScheduledTask.interval must be > 0");
        }
        if (lockTtl == null || lockTtl.compareTo(interval) <= 0) {
            // lockTtl 必须 > interval, 否则锁可能在下一轮 tick 之前过期被错抢
            throw new IllegalArgumentException("ClusterScheduledTask.lockTtl must be > interval");
        }
        if (runnable == null) {
            throw new IllegalArgumentException("ClusterScheduledTask.runnable must not be null");
        }
    }
}
