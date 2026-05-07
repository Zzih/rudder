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

package io.github.zzih.rudder.service.coordination.cache;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.coordination.BroadcastEvent;
import io.github.zzih.rudder.service.coordination.NodeIdProvider;
import io.github.zzih.rudder.service.coordination.RedisBroadcaster;
import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目唯一的全局缓存入口。所有跨节点缓存走这里;策略由 {@link GlobalCacheKey} 上的 {@link CacheSpec} 声明,
 * 业务方调用时不需要关心 L1/L2、TTL、单 flight、广播等细节。
 *
 * <h2>两种模式(由 spec 决定,业务方无感)</h2>
 *
 * <ul>
 *   <li><b>LOCAL</b> —— L1 Caffeine + Redis 仅广播失效。Redis 不存数据。
 *       适合小配置(LLM / FILE / DIALECT 等 12 项)。</li>
 *   <li><b>SHARED</b> —— L1 Caffeine + L2 Redis 直存(带 TTL) + 跨节点单 flight + 广播失效。
 *       适合大数据集 / 跨节点共享 / 源拉取贵的场景(metadata 数据等)。</li>
 * </ul>
 *
 * <h2>多节点正确性</h2>
 *
 * <ul>
 *   <li>SHARED 写顺序:先 L2 → 再 L1 → 再广播,保证其他节点收到广播后下次查能拿到新值</li>
 *   <li>SHARED L2 强制带 TTL,作为广播丢失的兜底</li>
 *   <li>SHARED 单 flight 用 Redis SETNX 抢锁,只允许一个节点拉源</li>
 *   <li>LOCAL 由 Caffeine 本地 TTL 兜底广播丢失</li>
 *   <li>跨节点广播复用 {@link RedisBroadcaster},nodeId 过滤等管道层一并复用</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // LOCAL —— 业务方不传 type
 * FileStorage s = cache.getOrLoad(GlobalCacheKey.FILE, () -> buildFileStorage());
 * cache.invalidate(GlobalCacheKey.FILE);
 *
 * // SHARED —— 业务方传 Class / TypeReference 用于 L2 反序列化
 * List<TableMeta> tables = cache.getOrLoad(
 *         GlobalCacheKey.METADATA_DATA, "ds_prod:db1:tables",
 *         new TypeReference<List<TableMeta>>(){},
 *         () -> metastore.listTables(...));
 * cache.invalidateByPrefix(GlobalCacheKey.METADATA_DATA, "ds_prod:");  // 整组清
 * }</pre>
 */
@Slf4j
@Service
public class GlobalCacheService {

    private static final String CHANNEL = RedisNaming.Channels.GLOBAL_CACHE_INVALIDATE;
    private static final String L2_PREFIX = RedisNaming.Cache.DATA_PREFIX;
    private static final String LOCK_PREFIX = RedisNaming.Cache.LOCK_PREFIX;

    /** 仅删除自己持有的锁(token 匹配)的 Lua 脚本,GET+DEL 原子,避免误删他人锁。 */
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    /** 单 entry 用空 subKey 占位(Caffeine 不允许 null key)。 */
    private static final String SINGLETON = "";

    /** 单 flight 锁 TTL,持锁节点崩了至多卡这么久。 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);
    /** 等持锁节点写完 L2 的最长等待。超过这个时间自己兜底拉一次源。 */
    private static final long SINGLE_FLIGHT_WAIT_MS = 5_000L;
    private static final long SINGLE_FLIGHT_POLL_MS = 50L;

    private final StringRedisTemplate redis;
    private final RedisBroadcaster<CacheEvent> broadcaster;
    private final Map<GlobalCacheKey, Cache<String, Object>> caches = new EnumMap<>(GlobalCacheKey.class);

    public GlobalCacheService(NodeIdProvider nodeIdProvider, StringRedisTemplate redis,
                              RedisMessageListenerContainer container) {
        this.redis = redis;
        this.broadcaster = new RedisBroadcaster<>(
                CHANNEL, nodeIdProvider.nodeId(), redis, container, CacheEvent.class);
        for (GlobalCacheKey k : GlobalCacheKey.values()) {
            CacheSpec spec = k.spec();
            caches.put(k, Caffeine.newBuilder()
                    .expireAfterWrite(spec.l1Ttl())
                    .maximumSize(spec.l1MaxSize())
                    .removalListener((subKey, value, cause) -> closeIfCloseable(value))
                    .build());
        }
    }

    /** 失效 / TTL 过期时统一关闭 AutoCloseable 资源,防 client / 句柄泄漏。 */
    private void closeIfCloseable(Object value) {
        if (value instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                log.warn("Failed to close evicted cache value {}: {}", value.getClass().getSimpleName(),
                        e.getMessage());
            }
        }
    }

    @PostConstruct
    void subscribe() {
        broadcaster.subscribe(this::onRemoteEvent);
    }

    /** Spring 优雅停机:清空 entry 触发 removalListener 释放 AutoCloseable。SIGKILL 下不走。 */
    @PreDestroy
    void shutdown() {
        for (Cache<String, Object> c : caches.values()) {
            c.invalidateAll();
            c.cleanUp();
        }
    }

    // ==================== LOCAL 模式 API(无 type) ====================

    public <T> T getOrLoad(GlobalCacheKey key, Supplier<T> loader) {
        return getOrLoad(key, SINGLETON, loader);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(GlobalCacheKey key, String subKey, Supplier<T> loader) {
        requireMode(key, CacheSpec.Mode.LOCAL);
        return (T) caches.get(key).get(subKey, k -> loader.get());
    }

    public <T> T get(GlobalCacheKey key) {
        return get(key, SINGLETON);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(GlobalCacheKey key, String subKey) {
        return (T) caches.get(key).getIfPresent(subKey);
    }

    public void put(GlobalCacheKey key, Object value) {
        put(key, SINGLETON, value);
    }

    public void put(GlobalCacheKey key, String subKey, Object value) {
        requireMode(key, CacheSpec.Mode.LOCAL);
        Cache<String, Object> cache = caches.get(key);
        Object prev = cache.getIfPresent(subKey);
        if (Objects.equals(prev, value)) {
            // 值未变,跳过广播。轮询 / 事件 handler 反复 put 同一 key 会触发 invalidation 风暴 +
            // closeIfCloseable 关掉别人正在用的 client 句柄,这里幂等检查止血。
            return;
        }
        if (value == null) {
            cache.invalidate(subKey);
        } else {
            cache.put(subKey, value);
        }
        broadcastInvalidation(key, subKey, false, false);
    }

    // ==================== SHARED 模式 API(带 type 用于 L2 反序列化) ====================

    public <T> T getOrLoad(GlobalCacheKey key, String subKey, Class<T> type, Supplier<T> loader) {
        requireMode(key, CacheSpec.Mode.SHARED);
        return sharedGetOrLoad(key, subKey, loader, json -> JsonUtils.fromJson(json, type));
    }

    public <T> T getOrLoad(GlobalCacheKey key, String subKey, TypeReference<T> typeRef, Supplier<T> loader) {
        requireMode(key, CacheSpec.Mode.SHARED);
        return sharedGetOrLoad(key, subKey, loader, json -> JsonUtils.fromJson(json, typeRef));
    }

    // ==================== 失效 API(两种模式通用) ====================

    public void invalidate(GlobalCacheKey key) {
        invalidate(key, SINGLETON);
    }

    public void invalidate(GlobalCacheKey key, String subKey) {
        if (key.spec().mode() == CacheSpec.Mode.SHARED) {
            try {
                redis.delete(l2Key(key, subKey));
            } catch (Exception e) {
                log.warn("invalidate L2 failed key={} subKey={}: {}", key, subKey, e.getMessage());
            }
        }
        caches.get(key).invalidate(subKey);
        broadcastInvalidation(key, subKey, false, false);
    }

    public void invalidateAll(GlobalCacheKey key) {
        if (key.spec().mode() == CacheSpec.Mode.SHARED) {
            scanAndUnlink(L2_PREFIX + key.name() + ":*");
        }
        caches.get(key).invalidateAll();
        broadcastInvalidation(key, null, true, false);
    }

    /**
     * 按 subKey 前缀失效。SHARED 模式 SCAN+UNLINK 清 L2;LOCAL/SHARED 都会按前缀清本地 + 广播。
     * 用于"按 datasource 整组清 metadata 缓存"这类场景。
     */
    public void invalidateByPrefix(GlobalCacheKey key, String subKeyPrefix) {
        if (subKeyPrefix == null || subKeyPrefix.isEmpty()) {
            invalidateAll(key);
            return;
        }
        if (key.spec().mode() == CacheSpec.Mode.SHARED) {
            scanAndUnlink(L2_PREFIX + key.name() + ":" + subKeyPrefix + "*");
        }
        // Caffeine 没有原生 prefix-invalidate,asMap 视图是 ConcurrentMap,可用 keySet().removeIf
        caches.get(key).asMap().keySet().removeIf(k -> k.startsWith(subKeyPrefix));
        broadcastInvalidation(key, subKeyPrefix, false, true);
    }

    // ==================== 内部:SHARED 执行路径 ====================

    @FunctionalInterface
    private interface JsonReader<T> {

        T read(String json);
    }

    @SuppressWarnings("unchecked")
    private <T> T sharedGetOrLoad(GlobalCacheKey key, String subKey,
                                  Supplier<T> loader, JsonReader<T> deser) {
        Cache<String, Object> l1 = caches.get(key);
        Object hit = l1.getIfPresent(subKey);
        if (hit != null) {
            return (T) hit;
        }
        // L2
        T fromL2 = readFromL2(key, subKey, deser);
        if (fromL2 != null) {
            l1.put(subKey, fromL2);
            return fromL2;
        }
        // L2 miss
        if (key.spec().singleFlight()) {
            return sharedLoadWithSingleFlight(key, subKey, loader, deser, l1);
        }
        return sharedLoadDirect(key, subKey, loader, l1);
    }

    private <T> T readFromL2(GlobalCacheKey key, String subKey, JsonReader<T> deser) {
        try {
            String json = redis.opsForValue().get(l2Key(key, subKey));
            if (json == null) {
                return null;
            }
            return deser.read(json);
        } catch (Exception e) {
            log.warn("read L2 failed key={} subKey={}: {}", key, subKey, e.getMessage());
            return null;
        }
    }

    private <T> T sharedLoadDirect(GlobalCacheKey key, String subKey,
                                   Supplier<T> loader, Cache<String, Object> l1) {
        T fresh = loader.get();
        if (fresh != null) {
            writeL2(key, subKey, fresh);
            l1.put(subKey, fresh);
        }
        return fresh;
    }

    private <T> T sharedLoadWithSingleFlight(GlobalCacheKey key, String subKey,
                                             Supplier<T> loader, JsonReader<T> deser,
                                             Cache<String, Object> l1) {
        String lockKey = LOCK_PREFIX + key.name() + ":" + subKey;
        String token = UUID.randomUUID().toString();
        Boolean got = null;
        try {
            got = redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        } catch (Exception e) {
            log.warn("single flight lock failed key={} subKey={}: {}", key, subKey, e.getMessage());
        }
        if (Boolean.TRUE.equals(got)) {
            try {
                return sharedLoadDirect(key, subKey, loader, l1);
            } finally {
                releaseLock(lockKey, token);
            }
        }
        // 没抢到 → 短轮询 L2
        long deadline = System.currentTimeMillis() + SINGLE_FLIGHT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(SINGLE_FLIGHT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            T fromL2 = readFromL2(key, subKey, deser);
            if (fromL2 != null) {
                l1.put(subKey, fromL2);
                return fromL2;
            }
        }
        // 等不到 → 兜底自己拉(锁可能已过期或持锁节点崩了)
        log.warn("single flight wait timeout key={} subKey={}, fallback to direct load", key, subKey);
        return sharedLoadDirect(key, subKey, loader, l1);
    }

    private void writeL2(GlobalCacheKey key, String subKey, Object value) {
        try {
            String json = JsonUtils.toJson(value);
            redis.opsForValue().set(l2Key(key, subKey), json, key.spec().l2Ttl());
        } catch (Exception e) {
            log.warn("write L2 failed key={} subKey={}: {}", key, subKey, e.getMessage());
        }
    }

    private void releaseLock(String lockKey, String token) {
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        } catch (Exception e) {
            log.warn("release lock failed {}: {}", lockKey, e.getMessage());
        }
    }

    private void scanAndUnlink(String matchPattern) {
        ScanOptions options = ScanOptions.scanOptions().match(matchPattern).count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                redis.unlink(cursor.next());
            }
        } catch (Exception e) {
            log.warn("scan & unlink failed pattern={}: {}", matchPattern, e.getMessage());
        }
    }

    private static String l2Key(GlobalCacheKey key, String subKey) {
        return L2_PREFIX + key.name() + ":" + subKey;
    }

    // ==================== 内部:广播 + 远端事件处理 ====================

    private void broadcastInvalidation(GlobalCacheKey key, String subKey, boolean allKeys, boolean prefixed) {
        broadcaster.send(new CacheEvent(key.name(), subKey, allKeys, prefixed, broadcaster.nodeId()));
    }

    /** 远端节点发来的失效事件(RedisBroadcaster 已过滤掉自己发的)。 */
    private void onRemoteEvent(CacheEvent event) {
        if (event.key() == null) {
            return;
        }
        GlobalCacheKey key;
        try {
            key = GlobalCacheKey.valueOf(event.key());
        } catch (IllegalArgumentException unknown) {
            return;
        }
        Cache<String, Object> c = caches.get(key);
        if (event.allKeys()) {
            c.invalidateAll();
        } else if (event.subKey() != null) {
            if (event.prefixed()) {
                c.asMap().keySet().removeIf(k -> k.startsWith(event.subKey()));
            } else {
                c.invalidate(event.subKey());
            }
        }
    }

    private static void requireMode(GlobalCacheKey key, CacheSpec.Mode expected) {
        if (key.spec().mode() != expected) {
            throw new IllegalStateException(
                    "GlobalCacheKey " + key + " is " + key.spec().mode()
                            + " mode, but called with API for " + expected
                            + " mode. Check CacheSpec on the enum.");
        }
    }

    /** 跨节点缓存失效消息体。Jackson 反序列化需要公开记录。 */
    public record CacheEvent(String key, String subKey, boolean allKeys,
            boolean prefixed, String originNodeId)
            implements
                BroadcastEvent {
    }
}
