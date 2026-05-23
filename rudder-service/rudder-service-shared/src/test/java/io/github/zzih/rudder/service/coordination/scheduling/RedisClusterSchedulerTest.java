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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.service.coordination.NodeIdProvider;
import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisClusterSchedulerTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private NodeIdProvider nodeIdProvider;

    private RedisClusterScheduler scheduler;

    private static final String TASK_KEY = "rudder:dataperm:reconcile";
    private static final String LOCK_KEY = RedisNaming.Scheduler.LOCK_PREFIX + TASK_KEY;
    private static final String LAST_END_KEY = RedisNaming.Scheduler.LAST_END_PREFIX + TASK_KEY;
    private static final String TRIGGER_KEY = RedisNaming.Scheduler.TRIGGER_PREFIX + TASK_KEY;

    @BeforeEach
    void setup() {
        scheduler = new RedisClusterScheduler(redis, nodeIdProvider);
    }

    private ClusterScheduledTask task(Runnable r) {
        return new ClusterScheduledTask(TASK_KEY, Duration.ofMinutes(5), Duration.ofMinutes(10), r);
    }

    @Test
    @DisplayName("schedule 注册任务可重复覆盖")
    void scheduleRegisters() {
        scheduler.schedule(task(() -> {
        }));
        assertEquals(1, scheduler.registeredTasks().size());

        scheduler.schedule(task(() -> {
        })); // 覆盖
        assertEquals(1, scheduler.registeredTasks().size());
    }

    @Test
    @DisplayName("triggerNow 未注册 key 仅 warn 不抛异常,且不打标记")
    void triggerNowUnregisteredIgnored() {
        scheduler.triggerNow("unknown-key", "test");
        verify(redis, never()).opsForValue();
    }

    @Test
    @DisplayName("triggerNow 已注册 key 打标记")
    void triggerNowMarks() {
        scheduler.schedule(task(() -> {
        }));
        when(redis.opsForValue()).thenReturn(valueOps);

        scheduler.triggerNow(TASK_KEY, "approval-approved");

        verify(valueOps).set(eq(TRIGGER_KEY), eq("approval-approved"), any(Duration.class));
    }

    @Test
    @DisplayName("tick: 抢锁失败 → 不执行任务")
    void tickLockAcquireFailureSkips() {
        AtomicInteger runCount = new AtomicInteger();
        scheduler.schedule(task(runCount::incrementAndGet));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of(TRIGGER_KEY, LAST_END_KEY))).thenReturn(Arrays.asList(null, null));
        when(nodeIdProvider.nodeId()).thenReturn("node-1");
        when(valueOps.setIfAbsent(eq(LOCK_KEY), eq("node-1"), any(Duration.class)))
                .thenReturn(false);

        scheduler.tick();
        scheduler.awaitWorkerIdle(java.time.Duration.ofSeconds(2));

        assertEquals(0, runCount.get());
    }

    @Test
    @DisplayName("tick: 抢锁成功 → 跑任务 → 记 lastEnd + 释放锁")
    void tickLockAcquireSuccessRunsAndReleases() {
        AtomicInteger runCount = new AtomicInteger();
        scheduler.schedule(task(runCount::incrementAndGet));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of(TRIGGER_KEY, LAST_END_KEY))).thenReturn(Arrays.asList(null, null));
        when(nodeIdProvider.nodeId()).thenReturn("node-1");
        when(valueOps.setIfAbsent(eq(LOCK_KEY), eq("node-1"), any(Duration.class)))
                .thenReturn(true);

        scheduler.tick();
        scheduler.awaitWorkerIdle(java.time.Duration.ofSeconds(2));

        assertEquals(1, runCount.get());
        // 任务结束记录 lastEnd
        verify(valueOps).set(eq(LAST_END_KEY), anyString(), any(Duration.class));
        // 任务结束消费 trigger 标记
        verify(redis).delete(TRIGGER_KEY);
        // 任务结束安全释放锁
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq("node-1"));
    }

    @Test
    @DisplayName("tick: 周期未到 + 无 trigger → 不抢锁")
    void tickIntervalNotReachedSkipsLock() {
        scheduler.schedule(task(() -> {
        }));

        when(redis.opsForValue()).thenReturn(valueOps);
        // 上轮结束 1 秒前, interval=5min, 远未到
        when(valueOps.multiGet(List.of(TRIGGER_KEY, LAST_END_KEY)))
                .thenReturn(Arrays.asList(null, String.valueOf(System.currentTimeMillis() - 1_000L)));

        scheduler.tick();
        scheduler.awaitWorkerIdle(java.time.Duration.ofSeconds(2));

        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tick: trigger 标记存在 → 即使周期未到也尝试抢锁")
    void tickTriggerForcesAttempt() {
        scheduler.schedule(task(() -> {
        }));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of(TRIGGER_KEY, LAST_END_KEY)))
                .thenReturn(Arrays.asList("approval-approved", null));
        when(nodeIdProvider.nodeId()).thenReturn("node-1");
        when(valueOps.setIfAbsent(eq(LOCK_KEY), eq("node-1"), any(Duration.class)))
                .thenReturn(false); // 假设没抢到

        scheduler.tick();
        scheduler.awaitWorkerIdle(java.time.Duration.ofSeconds(2));

        // 关键: 即使 lastEnd 很近, 也尝试 setIfAbsent (因为 trigger)
        verify(valueOps, atLeastOnce()).setIfAbsent(eq(LOCK_KEY), eq("node-1"), any(Duration.class));
    }

    @Test
    @DisplayName("tick: 任务抛异常 → 仍记 lastEnd + 释放锁")
    void tickTaskThrowsStillReleases() {
        scheduler.schedule(task(() -> {
            throw new RuntimeException("boom");
        }));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of(TRIGGER_KEY, LAST_END_KEY))).thenReturn(Arrays.asList(null, null));
        when(nodeIdProvider.nodeId()).thenReturn("node-1");
        when(valueOps.setIfAbsent(eq(LOCK_KEY), eq("node-1"), any(Duration.class)))
                .thenReturn(true);

        scheduler.tick();
        scheduler.awaitWorkerIdle(java.time.Duration.ofSeconds(2));

        verify(valueOps, times(1)).set(eq(LAST_END_KEY), anyString(), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq("node-1"));
    }

    @Test
    @DisplayName("triggerNow 多次幂等 (SET 重复覆盖, 视为一次)")
    void triggerNowIdempotent() {
        scheduler.schedule(task(() -> {
        }));
        when(redis.opsForValue()).thenReturn(valueOps);

        scheduler.triggerNow(TASK_KEY, "r1");
        scheduler.triggerNow(TASK_KEY, "r2");
        scheduler.triggerNow(TASK_KEY, "r3");

        // 3 次 SET 都打到同一 key, Redis 层语义即"覆盖", 视作一次
        verify(valueOps, times(3)).set(eq(TRIGGER_KEY), anyString(), any(Duration.class));
        assertTrue(true);
    }
}
