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

package io.github.zzih.rudder.service.coordination.ratelimit;

import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全集群共享的固定窗口限流服务。
 *
 * <p>跟 {@code GlobalCacheService}(缓存)、{@code PubSubSignalRegistry}(信号)
 * 并列,是协调原语的第三种语义:**带 TTL 的原子计数器**。
 *
 * <p>算法:Redis {@code INCR + EXPIRE} 固定窗口。Redis 单线程保证 INCR 原子,
 * 同 (scope, key) 在不同节点的请求打到同一 Redis key,**配额跨节点共享**(不是每节点独立)。
 *
 * <h2>使用</h2>
 *
 * <pre>{@code
 * if (!rateLimitService.tryAcquire("login", clientIp, 5, Duration.ofMinutes(1))) {
 *     throw new BizException(SystemErrorCode.TOO_MANY_REQUESTS, "login limit exceeded");
 * }
 * }</pre>
 *
 * <h2>语义保证</h2>
 *
 * <ul>
 *   <li>scope 隔离不同业务(如 {@code "login"} / {@code "mcp:tool"} / {@code "execute"})</li>
 *   <li>key 是限流主键(IP / userId / tokenId 等)</li>
 *   <li>固定窗口的边界突增问题(59-60s 内可能 2× limit)在大多数场景可接受;
 *       需更平滑可未来加 {@code tryAcquireSlidingLog}</li>
 *   <li>Redis 不可达时:日志 warn 但**放行** —— 限流不应成为可用性故障点</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /** 计数 TTL = 窗口长度 + 1 分钟缓冲(防 Redis 重启时窗口对齐误差)。 */
    private static final Duration TTL_BUFFER = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    /**
     * 尝试获取令牌(全集群共享配额)。
     *
     * @param scope         限流场景(如 {@code "login"} / {@code "mcp:tool"} / {@code "execute"})
     * @param key           限流主键(如 IP / userId / tokenId)
     * @param maxPermits    窗口内允许的请求数。{@code <= 0} 表示不限流(直接返 true)
     * @param window        窗口时长
     * @return 获取成功返回 true,超限返回 false
     */
    public boolean tryAcquire(String scope, String key, int maxPermits, Duration window) {
        if (maxPermits <= 0 || scope == null || key == null) {
            return true;
        }
        long windowEpoch = System.currentTimeMillis() / window.toMillis();
        String redisKey = RedisNaming.RateLimit.PREFIX + scope + ":" + key + ":" + windowEpoch;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window.plus(TTL_BUFFER));
            }
            return count == null || count <= maxPermits;
        } catch (Exception e) {
            log.warn("rate limit Redis check failed, allowing: scope={}, key={}, error={}",
                    scope, key, e.getMessage());
            return true;
        }
    }

    /** 计数器归零(测试 / 手动解除限流)。 */
    public void reset(String scope, String key, Duration window) {
        if (scope == null || key == null) {
            return;
        }
        long windowEpoch = System.currentTimeMillis() / window.toMillis();
        try {
            redis.delete(RedisNaming.RateLimit.PREFIX + scope + ":" + key + ":" + windowEpoch);
        } catch (Exception e) {
            log.warn("rate limit reset failed: scope={}, key={}, error={}",
                    scope, key, e.getMessage());
        }
    }
}
