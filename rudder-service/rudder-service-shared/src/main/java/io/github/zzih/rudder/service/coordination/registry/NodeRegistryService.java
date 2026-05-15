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

package io.github.zzih.rudder.service.coordination.registry;

import io.github.zzih.rudder.dao.enums.ServiceType;
import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 节点心跳注册原语。Liveness 由 Redis TTL 表达,每节点自己发布,其它节点 SCAN 全集。
 *
 * <p>跟 {@code RateLimitService}(限流计数器)、{@code WorkflowEditLockService}(编辑锁)
 * 并列,是协调原语的一种:**带 TTL 的自发布 KV + SCAN 全集**。
 *
 * <p>Value 编码:taskCount(整数 string)。仅 Worker 自报有意义,Server 端写 0。
 */
@Service
@RequiredArgsConstructor
public class NodeRegistryService {

    private final StringRedisTemplate redis;

    /** 写入或刷新自身心跳,过期靠 TTL 自然清理。 */
    public void publish(ServiceType type, String host, int port, int taskCount, Duration ttl) {
        redis.opsForValue().set(keyOf(type, host, port), String.valueOf(taskCount), ttl);
    }

    /** 主动撤销自身心跳,用于优雅下线。 */
    public void revoke(ServiceType type, String host, int port) {
        redis.delete(keyOf(type, host, port));
    }

    /** 全集存活节点 key,reconciler 对账用。 */
    public Set<String> aliveKeys() {
        Set<String> keys = new HashSet<>();
        scanKeys(RedisNaming.Registry.NODE_PREFIX + "*", keys::add);
        return keys;
    }

    /** 全集存活节点快照(含 taskCount),单次 SCAN + MGET。 */
    public List<NodeAddress> aliveAll() {
        return scanAddresses(RedisNaming.Registry.NODE_PREFIX + "*");
    }

    /** 按类型拉存活节点快照(含 taskCount),单次 SCAN + MGET,无 N+1。 */
    public List<NodeAddress> aliveByType(ServiceType type) {
        return scanAddresses(RedisNaming.Registry.NODE_PREFIX + type.name() + ":*");
    }

    private List<NodeAddress> scanAddresses(String pattern) {
        List<String> keys = new ArrayList<>();
        scanKeys(pattern, keys::add);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        List<NodeAddress> out = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            NodeAddress addr = parseKey(keys.get(i), values == null ? null : values.get(i));
            if (addr != null) {
                out.add(addr);
            }
        }
        return out;
    }

    /** Reconciler 用:基于 entity 字段还原 key 与 aliveAll() 比对。 */
    public String keyOf(ServiceType type, String host, int port) {
        return RedisNaming.Registry.NODE_PREFIX + type.name() + ":" + host + ":" + port;
    }

    private void scanKeys(String pattern, Consumer<String> consumer) {
        try (
                Cursor<String> cursor = redis.scan(
                        ScanOptions.scanOptions().match(pattern).count(200).build())) {
            cursor.forEachRemaining(consumer);
        }
    }

    /** 格式 {@code rudder:registry:node:{TYPE}:{HOST}:{PORT}};中段可含 ":" 以兼容 IPv6 host。 */
    private static NodeAddress parseKey(String key, String value) {
        if (!key.startsWith(RedisNaming.Registry.NODE_PREFIX)) {
            return null;
        }
        String rest = key.substring(RedisNaming.Registry.NODE_PREFIX.length());
        int firstColon = rest.indexOf(':');
        int lastColon = rest.lastIndexOf(':');
        if (firstColon < 0 || lastColon <= firstColon) {
            return null;
        }
        try {
            ServiceType type = ServiceType.valueOf(rest.substring(0, firstColon));
            String host = rest.substring(firstColon + 1, lastColon);
            int port = Integer.parseInt(rest.substring(lastColon + 1));
            return new NodeAddress(type, host, port, parseTaskCount(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseTaskCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
