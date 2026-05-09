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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.coordination.RedisNaming;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流编辑锁。Redis SET NX EX,纯 UX 防 UI 同时进编辑;数据安全由 service.update 的 contentHash 校验兜底。
 *
 * <p>锁 holder 只用 userId 区分,同一 user 多 tab 视为同一持有者(后端只看 userId)。
 * 60s TTL,前端 30s 心跳续期。无 force 接管 —— 等 TTL 自然释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEditLockService {

    public static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public record Holder(Long userId, String username, long acquiredAt) {
    }

    /** 抢锁。无锁/锁已过期/holder 是自己 → 成功;他人持锁 → 返当前 holder。 */
    public Optional<Holder> tryAcquire(Long workflowCode, Long userId, String username) {
        String key = key(workflowCode);
        Optional<Holder> existing = peek(workflowCode);
        if (existing.isPresent() && !existing.get().userId.equals(userId)) {
            return existing;
        }
        Holder fresh = new Holder(userId, username, System.currentTimeMillis());
        redis.opsForValue().set(key, JsonUtils.toJson(fresh), TTL);
        return Optional.empty();
    }

    /** 心跳续期。仅 holder.userId == userId 时延长 TTL。 */
    public boolean heartbeat(Long workflowCode, Long userId) {
        Optional<Holder> existing = peek(workflowCode);
        if (existing.isEmpty() || !existing.get().userId.equals(userId)) {
            return false;
        }
        return Boolean.TRUE.equals(redis.expire(key(workflowCode), TTL));
    }

    /** 释放。仅 holder.userId == userId 时删除。 */
    public void release(Long workflowCode, Long userId) {
        Optional<Holder> existing = peek(workflowCode);
        if (existing.isPresent() && existing.get().userId.equals(userId)) {
            redis.delete(key(workflowCode));
        }
    }

    /** 当前持锁人。供 service.update 校验 + 前端 GET /lock 查谁在编辑。 */
    public Optional<Holder> peek(Long workflowCode) {
        try {
            String raw = redis.opsForValue().get(key(workflowCode));
            return raw == null ? Optional.empty() : Optional.ofNullable(JsonUtils.fromJson(raw, Holder.class));
        } catch (Exception e) {
            log.warn("read edit lock failed: code={}, err={}", workflowCode, e.getMessage());
            return Optional.empty();
        }
    }

    private String key(Long workflowCode) {
        return RedisNaming.EditLock.PREFIX + "workflow:" + workflowCode;
    }
}
