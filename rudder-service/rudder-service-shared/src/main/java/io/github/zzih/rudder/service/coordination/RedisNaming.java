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

package io.github.zzih.rudder.service.coordination;

/**
 * 项目所有 Redis key / pub-sub channel 命名空间集中定义。
 *
 * <p>新增协调原语必须在这里登记前缀,避免散在各处导致命名冲突
 * (尤其是 SCAN/UNLINK 按前缀清时,前缀互相包含会误删别家数据)。
 *
 * <h2>规约</h2>
 *
 * <ul>
 *   <li>所有 key / channel 一律以 {@value #ROOT} 起头,跟同 Redis 实例下其他服务隔离</li>
 *   <li>{@link Channels} —— Redis pub/sub 通道,不存数据</li>
 *   <li>{@link Cache} —— {@code GlobalCacheService} SHARED 模式 L2 + 单 flight 锁</li>
 * </ul>
 *
 * <p>不同子命名空间之间**不应有前缀包含关系**(例如 {@code rudder:cache:lock:} 不能是
 * {@code rudder:cache:} 的子串扫描目标),否则 SCAN 误命中。当前实现已规避。
 */
public final class RedisNaming {

    /** 顶层根前缀。改动会让所有已写入的 key/channel 失效,慎重。 */
    public static final String ROOT = "rudder";

    private RedisNaming() {
    }

    /** Pub/Sub 通道 —— 跨节点广播事件,Redis 不存数据。 */
    public static final class Channels {

        /** {@code GlobalCacheService} 缓存失效广播。 */
        public static final String GLOBAL_CACHE_INVALIDATE = ROOT + ":signal:global-cache";

        /** {@code ToolApprovalRegistry} 工具审批结果(批准/拒绝)跨节点送达。 */
        public static final String TOOL_APPROVAL = ROOT + ":signal:tool-approval";

        /** {@code StreamRegistry} agent SSE 流取消信号跨节点送达。 */
        public static final String STREAM_CANCEL = ROOT + ":signal:stream-cancel";

        private Channels() {
        }
    }

    /** 缓存命名空间 —— {@code GlobalCacheService} SHARED 模式专用。 */
    public static final class Cache {

        /** L2 缓存数据 key 前缀。完整 key:{@code rudder:cache:data:{KEY}:{subKey}}。 */
        public static final String DATA_PREFIX = ROOT + ":cache:data:";

        /** 单 flight 锁 key 前缀。完整 key:{@code rudder:cache:lock:{KEY}:{subKey}}。 */
        public static final String LOCK_PREFIX = ROOT + ":cache:lock:";

        private Cache() {
        }
    }

    /** 限流计数器命名空间 —— {@code RateLimitService} 专用。 */
    public static final class RateLimit {

        /** 计数器 key 前缀。完整 key:{@code rudder:ratelimit:{scope}:{key}:{windowEpoch}}。 */
        public static final String PREFIX = ROOT + ":ratelimit:";

        private RateLimit() {
        }
    }

    /** 编辑锁命名空间 —— 悲观编辑锁(纯 UX,数据安全靠 hash 兜底)。 */
    public static final class EditLock {

        /** 锁 key 前缀。完整 key:{@code rudder:editlock:{resource}:{id}}。 */
        public static final String PREFIX = ROOT + ":editlock:";

        private EditLock() {
        }
    }

}
