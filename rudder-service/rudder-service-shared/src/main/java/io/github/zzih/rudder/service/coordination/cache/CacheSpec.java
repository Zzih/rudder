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

import java.time.Duration;

/**
 * 单个 {@link GlobalCacheKey} 的缓存策略描述。两种模式:
 *
 * <ul>
 *   <li>{@link Mode#LOCAL} —— 每节点本地 Caffeine + Redis 仅广播失效。
 *       适合"小数据 + 跨节点最终一致"的配置类(LLM / FILE / DIALECT 等)。</li>
 *   <li>{@link Mode#SHARED} —— 本地 Caffeine(L1) + Redis 直存(L2) + 跨节点单 flight + 失效广播。
 *       适合"大数据集 + 跨节点共享 + 源拉取贵"的场景(metadata 表 / 列等)。</li>
 * </ul>
 *
 * <p>业务方不直接接触 CacheSpec —— 它只在 {@link GlobalCacheKey} 内声明,
 * {@link GlobalCacheService} 内部根据 spec 选执行路径,API 一致。
 */
public final class CacheSpec {

    public enum Mode {
        LOCAL, SHARED
    }

    private final Mode mode;
    private final Duration l1Ttl;
    private final long l1MaxSize;
    /** 仅 SHARED 用,LOCAL 为 null。 */
    private final Duration l2Ttl;
    /** 仅 SHARED 用。true 表示 L2 miss 时跨节点用 Redis SETNX 抢锁,只让一个节点拉源。 */
    private final boolean singleFlight;

    private CacheSpec(Mode mode, Duration l1Ttl, long l1MaxSize,
                      Duration l2Ttl, boolean singleFlight) {
        this.mode = mode;
        this.l1Ttl = l1Ttl;
        this.l1MaxSize = l1MaxSize;
        this.l2Ttl = l2Ttl;
        this.singleFlight = singleFlight;
    }

    /** LOCAL 模式:每节点本地 Caffeine + Redis 广播失效。 */
    public static CacheSpec local(Duration l1Ttl, long l1MaxSize) {
        return new CacheSpec(Mode.LOCAL, l1Ttl, l1MaxSize, null, false);
    }

    /** SHARED 模式:L1 + L2(Redis 直存,带 TTL) + 可选跨节点单 flight + 广播失效。 */
    public static CacheSpec shared(Duration l1Ttl, long l1MaxSize,
                                   Duration l2Ttl, boolean singleFlight) {
        if (l2Ttl == null || l2Ttl.isZero() || l2Ttl.isNegative()) {
            throw new IllegalArgumentException("SHARED cache must declare a positive l2Ttl");
        }
        return new CacheSpec(Mode.SHARED, l1Ttl, l1MaxSize, l2Ttl, singleFlight);
    }

    public Mode mode() {
        return mode;
    }

    public Duration l1Ttl() {
        return l1Ttl;
    }

    public long l1MaxSize() {
        return l1MaxSize;
    }

    public Duration l2Ttl() {
        return l2Ttl;
    }

    public boolean singleFlight() {
        return singleFlight;
    }
}
