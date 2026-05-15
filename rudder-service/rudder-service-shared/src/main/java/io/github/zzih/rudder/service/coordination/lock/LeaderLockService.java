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

package io.github.zzih.rudder.service.coordination.lock;

import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 单 leader 接管协调任务的抢占 / 续约锁,跟 {@link WorkflowEditLockService}(资源编辑锁)并列。
 *
 * <p>原子语义:不存在则抢,owner 匹配则续 TTL,否则失败。Lua 单脚本保证全程原子,租约 TTL ≥ 2 倍
 * tick 周期时 leader 漏 1 次不失主。死后 TTL 自然到期,别的节点抢到后接管。
 *
 * <h2>使用</h2>
 *
 * <pre>{@code
 * if (leaderLockService.tryAcquireOrRenew("registry", selfId, Duration.ofSeconds(15))) {
 *     // 本节点为 leader,执行协调动作
 * }
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class LeaderLockService {

    private static final RedisScript<Long> ACQUIRE_OR_RENEW = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 0 then "
                    + "  redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]) return 1 "
                    + "elseif redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "  redis.call('pexpire', KEYS[1], ARGV[2]) return 1 "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /**
     * 尝试抢占或续约 leader 身份。
     *
     * @param resource 锁资源名(如 {@code "registry"}),内部拼成 {@code rudder:leader:{resource}}
     * @param ownerId  本节点唯一标识(UUID 等),续约时 owner 必须匹配
     * @param ttl      租约时长
     * @return true 本节点是 leader(刚抢到或续约成功),false 锁被他人持有
     */
    public boolean tryAcquireOrRenew(String resource, String ownerId, Duration ttl) {
        Long ok = redis.execute(ACQUIRE_OR_RENEW,
                List.of(keyOf(resource)),
                ownerId, String.valueOf(ttl.toMillis()));
        return ok != null && ok == 1L;
    }

    private static String keyOf(String resource) {
        return RedisNaming.LeaderLock.PREFIX + resource;
    }
}
