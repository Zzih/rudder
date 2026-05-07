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

package io.github.zzih.rudder.service.coordination.token;

import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 一次性凭证(one-shot token)的统一存储入口。跨节点共享,带 TTL 自动过期,{@link #consume} 原子核销。
 *
 * <p>跟 {@code GlobalCacheService}(缓存,可重复读)、{@code PubSubSignalRegistry}(信号,跨节点唤醒)
 * 并列,是协调原语的第三种语义:**写一次 → 读一次即焚**。
 *
 * <h2>典型场景</h2>
 *
 * <ul>
 *   <li>SSO state(防 CSRF / 钓鱼)</li>
 *   <li>邮箱验证链接 token</li>
 *   <li>重置密码 token</li>
 *   <li>API 防重放 nonce</li>
 *   <li>一次性下载链接</li>
 * </ul>
 *
 * <h2>使用</h2>
 *
 * <pre>{@code
 * private static final String SCOPE = "sso:state";
 * private static final Duration TTL = Duration.ofMinutes(5);
 *
 * // 发出凭证(登录前):
 * String state = UUID.randomUUID().toString();
 * tokenService.put(SCOPE, state, TTL);
 *
 * // 校验并核销(回调收到时):
 * if (!tokenService.consume(SCOPE, state)) {
 *     throw new BizException(SystemErrorCode.UNAUTHORIZED, "invalid or expired state");
 * }
 * }</pre>
 *
 * <h2>语义保证</h2>
 *
 * <ul>
 *   <li>{@link #put} 多次写同一 token 会覆盖(同 token 不应重复发,业务方负责保证唯一性)</li>
 *   <li>{@link #consume} 用 Redis {@code DEL} 返回值判断,原子保证"最多一个调用方拿到 true"</li>
 *   <li>跨节点共享(节点 A put,节点 B consume,正常工作)</li>
 *   <li>不存在 / 已过期 / 已消费过 都返回 false,业务方按"凭证无效"处理</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OneShotTokenService {

    private static final String KEY_PREFIX = RedisNaming.Token.PREFIX;

    private final StringRedisTemplate redis;

    /**
     * 写入一次性凭证。
     *
     * @param scope 凭证类别命名空间(如 {@code "sso:state"}、{@code "reset:password"})
     * @param token 凭证内容(通常是 UUID 或随机串)
     * @param ttl   存活时间,过期自动失效
     */
    public void put(String scope, String token, Duration ttl) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.opsForValue().set(buildKey(scope, token), "1", ttl);
    }

    /**
     * 校验并核销凭证(原子 DEL)。
     *
     * @return {@code true} 表示凭证存在且核销成功;{@code false} 表示不存在 / 已过期 / 已被其他线程核销过
     */
    public boolean consume(String scope, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Boolean removed = redis.delete(buildKey(scope, token));
        return Boolean.TRUE.equals(removed);
    }

    private static String buildKey(String scope, String token) {
        return KEY_PREFIX + scope + ":" + token;
    }
}
