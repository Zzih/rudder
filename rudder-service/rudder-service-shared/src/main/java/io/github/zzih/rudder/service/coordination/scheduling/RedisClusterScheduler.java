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

import io.github.zzih.rudder.service.coordination.NodeIdProvider;
import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ClusterScheduler} 的 Redis 实现。
 *
 * <p>各节点 30s 一次内部 tick;tick 时遍历本节点注册的 task,判定是否应跑(周期到 或 触发标记存在),
 * 然后用 {@code SETNX EX} 抢 Redis 锁。抢到的节点起心跳续约线程定期 {@code EXPIRE} 锁,
 * 任务结束用 Lua 脚本原子 CAS 释放锁(防 TTL 过期被抢后误删别节点的锁)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisClusterScheduler implements ClusterScheduler {

    /** 内部 tick 间隔。比最小 interval(>= 1min)密一档,保证及时性。 */
    private static final long TICK_INTERVAL_MS = 30_000L;

    /** worker 池大小。task 数量小(每节点几条),固定即可。 */
    private static final int WORKER_POOL_SIZE = 4;

    /** shutdown 时等待 worker 排空的上限,超时后强制 interrupt。 */
    private static final long SHUTDOWN_AWAIT_MS = 5_000L;

    /** 安全释放锁:仅当 value == 自己节点 id 才 DEL,避免误删别节点已抢到的锁。 */
    private static final RedisScript<Long> SAFE_RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** lastEnd / trigger 标记 key 的 TTL(防孤儿 key 永久占用)。 */
    private static final Duration STATE_KEY_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;
    private final NodeIdProvider nodeIdProvider;

    private final Map<String, ClusterScheduledTask> tasks = new ConcurrentHashMap<>();

    /** 心跳续约线程池。daemon,JVM 关闭时不阻塞。 */
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "cluster-scheduler-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** 独立 tick 线程,避免与 Spring 默认单线程 taskScheduler 共享导致业务 @Scheduled 慢任务 starve。 */
    private final ScheduledExecutorService tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-scheduler-tick");
        t.setDaemon(true);
        return t;
    });

    /** task 实际执行池。tick 仅派发,避免单个慢 task 阻塞下一轮 tick。 */
    private final ExecutorService workerExecutor = Executors.newFixedThreadPool(WORKER_POOL_SIZE, r -> {
        Thread t = new Thread(r, "cluster-scheduler-worker");
        t.setDaemon(true);
        return t;
    });

    /** 同一 task 重叠提交去重:tick A 抢锁的 task 还没跑完,tick B 不该再提交一次。 */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** shutdown 标志。被 interrupt 但跑完的 task 写 lastEnd 会污染新进程 shouldRun 判定。 */
    private volatile boolean stopping = false;

    /** 测试用:等本节点所有 worker 提交的 task 都跑完。生产路径不应调,会阻塞 tick 线程。 */
    void awaitWorkerIdle(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (!inFlight.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void schedule(ClusterScheduledTask task) {
        ClusterScheduledTask prev = tasks.put(task.key(), task);
        if (prev == null) {
            log.info("ClusterScheduler registered task: key={}, interval={}, lockTtl={}",
                    task.key(), task.interval(), task.lockTtl());
        } else {
            log.info("ClusterScheduler replaced task: key={}", task.key());
        }
    }

    @Override
    public void triggerNow(String key, String reason) {
        if (!tasks.containsKey(key)) {
            log.warn("ClusterScheduler.triggerNow called on unregistered key={} (reason={})", key, reason);
            return;
        }
        // SET 幂等;一段时间内多次 triggerNow 等价一次. TTL 足够大避免标记永不被消费.
        redis.opsForValue().set(triggerKey(key), reason, STATE_KEY_TTL);
        log.debug("ClusterScheduler.triggerNow marked: key={}, reason={}", key, reason);
    }

    @PostConstruct
    void startTicker() {
        tickExecutor.scheduleWithFixedDelay(this::tick,
                TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("ClusterScheduler ticker started: interval={}ms", TICK_INTERVAL_MS);
    }

    /** 内部 tick. 各节点都跑, 抢锁去重;task 实际执行扔到 workerExecutor,tick 立即返回继续派发下一个。 */
    void tick() {
        for (ClusterScheduledTask task : tasks.values()) {
            if (!inFlight.add(task.key())) {
                continue;
            }
            try {
                workerExecutor.execute(() -> {
                    try {
                        tryRunTask(task);
                    } catch (Exception e) {
                        log.error("ClusterScheduler worker failed: key={}", task.key(), e);
                    } finally {
                        inFlight.remove(task.key());
                    }
                });
            } catch (RejectedExecutionException e) {
                inFlight.remove(task.key());
                log.warn("ClusterScheduler worker pool rejected task: key={}", task.key());
            }
        }
    }

    private void tryRunTask(ClusterScheduledTask task) {
        if (!shouldRun(task)) {
            return;
        }
        if (!tryAcquireLock(task)) {
            return;
        }

        // 起心跳续约 —— 任务跑 N 倍 lockTtl 也不会被错抢
        ScheduledFuture<?> heartbeat = startHeartbeat(task);
        try {
            log.debug("ClusterScheduler running task: key={}, node={}", task.key(), nodeIdProvider.nodeId());
            task.runnable().run();
        } catch (Throwable t) {
            log.error("ClusterScheduler task threw: key={}", task.key(), t);
        } finally {
            heartbeat.cancel(true);
            // shutdown 中:不更新 lastEnd / 不释放锁。锁靠 TTL 过期,新进程从干净 lastEnd 继续。
            if (stopping) {
                return;
            }
            recordLastEnd(task);
            // 消费触发标记必须在释放锁前: 防止"释放锁但标记还在 → 下个 tick 又跑一次"
            redis.delete(triggerKey(task.key()));
            safeReleaseLock(task);
        }
    }

    private boolean shouldRun(ClusterScheduledTask task) {
        // 单次 MGET 合并 trigger + lastEnd 探测,减半 redis 往返。
        List<String> vals = redis.opsForValue().multiGet(List.of(triggerKey(task.key()), lastEndKey(task.key())));
        if (vals != null && vals.get(0) != null) {
            return true;
        }
        String lastEndRaw = vals == null ? null : vals.get(1);
        if (lastEndRaw == null) {
            return true;
        }
        long lastEnd;
        try {
            lastEnd = Long.parseLong(lastEndRaw);
        } catch (NumberFormatException ignore) {
            return true;
        }
        return System.currentTimeMillis() - lastEnd >= task.interval().toMillis();
    }

    private boolean tryAcquireLock(ClusterScheduledTask task) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(lockKey(task.key()), nodeIdProvider.nodeId(), task.lockTtl());
        return Boolean.TRUE.equals(ok);
    }

    private ScheduledFuture<?> startHeartbeat(ClusterScheduledTask task) {
        long renewMs = task.lockTtl().toMillis() / 3;
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                redis.expire(lockKey(task.key()), task.lockTtl());
            } catch (Exception e) {
                log.warn("ClusterScheduler heartbeat renew failed: key={}, err={}",
                        task.key(), e.getMessage());
            }
        }, renewMs, renewMs, TimeUnit.MILLISECONDS);
    }

    private void recordLastEnd(ClusterScheduledTask task) {
        try {
            redis.opsForValue().set(
                    lastEndKey(task.key()),
                    String.valueOf(System.currentTimeMillis()),
                    STATE_KEY_TTL);
        } catch (Exception e) {
            log.warn("ClusterScheduler lastEnd record failed: key={}, err={}",
                    task.key(), e.getMessage());
        }
    }

    private void safeReleaseLock(ClusterScheduledTask task) {
        try {
            redis.execute(SAFE_RELEASE_SCRIPT,
                    List.of(lockKey(task.key())),
                    nodeIdProvider.nodeId());
        } catch (Exception e) {
            log.warn("ClusterScheduler safe-release failed: key={}, err={}",
                    task.key(), e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        stopping = true;
        tickExecutor.shutdownNow();
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(SHUTDOWN_AWAIT_MS, TimeUnit.MILLISECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        heartbeatExecutor.shutdownNow();
    }

    /** 包私有, 供单测断言/清理用. */
    Map<String, ClusterScheduledTask> registeredTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    private String lockKey(String key) {
        return RedisNaming.Scheduler.LOCK_PREFIX + key;
    }

    private String lastEndKey(String key) {
        return RedisNaming.Scheduler.LAST_END_PREFIX + key;
    }

    private String triggerKey(String key) {
        return RedisNaming.Scheduler.TRIGGER_PREFIX + key;
    }
}
