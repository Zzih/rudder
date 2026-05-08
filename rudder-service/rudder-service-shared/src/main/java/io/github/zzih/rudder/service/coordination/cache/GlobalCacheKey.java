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
 * 全局缓存的 key 枚举。每项通过 {@link CacheSpec} 声明自己的策略,业务方调用 {@link GlobalCacheService}
 * 时不需要关心策略细节(L1/L2、TTL、单 flight、广播)。新增缓存类型在这里加一项即可。
 *
 * <p>选 spec 的指南:
 *
 * <ul>
 *   <li>小数据(单条配置) + 跨节点最终一致即可 → {@code CacheSpec.local(...)}</li>
 *   <li>大数据集 + 跨节点共享 + 源拉取贵(远端 / 慢源) → {@code CacheSpec.shared(...)}</li>
 * </ul>
 */
public enum GlobalCacheKey {

    // ==================== LOCAL: 配置缓存(每节点本地 + Redis 广播失效) ====================
    FILE(Defaults.CONFIG),
    RESULT(Defaults.CONFIG),
    RUNTIME(Defaults.CONFIG),
    LLM(Defaults.CONFIG),
    EMBEDDING(Defaults.CONFIG),
    VECTOR(Defaults.CONFIG),
    RERANK(Defaults.CONFIG),
    RAG_PIPELINE(Defaults.CONFIG),
    METADATA(Defaults.CONFIG),
    APPROVAL(Defaults.CONFIG),
    PUBLISH(Defaults.CONFIG),
    NOTIFICATION(Defaults.CONFIG),
    VERSION(Defaults.CONFIG),
    REDACTION(Defaults.CONFIG),
    DIALECT(Defaults.CONFIG),
    AUTH_SOURCE(Defaults.CONFIG),

    /** 多 entry:每个 (datasource, table, column) tag 一个 entry。 */
    METADATA_TAG(CacheSpec.local(Duration.ofMinutes(5), 10_000)),

    /**
     * MCP PAT 验证结果缓存(明文 token → TokenView)。
     *
     * <p>5 秒 TTL —— 同一 token 5 秒内连续调用只跑 1 次 bcrypt(80~100ms),其余命中本地 Caffeine。
     * 撤销 / 角色降级 / 审批激活时调 {@code invalidateAll(MCP_TOKEN_VIEW)} 整组清,
     * 所有节点下次调用各自重 bcrypt 一次(每用户感知 80ms,无感)。
     */
    MCP_TOKEN_VIEW(CacheSpec.local(Duration.ofSeconds(5), 10_000)),

    // ==================== SHARED: 大数据共享缓存(L1 + L2 Redis 直存 + 跨节点单 flight) ====================

    /**
     * 元数据数据(catalog / database / table / column 列表 + tableDetail + 搜索结果)。
     *
     * <ul>
     *   <li>跨节点共享:同 datasource 一个节点拉过,其他节点直接走 L2 拿到</li>
     *   <li>单 flight:同时 miss 时只让一个节点打 metadata 源(防 metastore 惊群)</li>
     *   <li>L2 TTL 30min:跟原 RedisMetadataCache 行为一致</li>
     *   <li>subKey 规约见 {@link MetadataCacheKeys}({@code {ds}:catalogs} / {@code {ds}:{cat}:{db}:tables} ...),
     *       支持 {@link GlobalCacheService#invalidateByPrefix} 按 datasource 整组清</li>
     * </ul>
     */
    METADATA_DATA(CacheSpec.shared(
            Duration.ofMinutes(5), // L1 TTL
            2000, // L1 maxSize
            Duration.ofMinutes(30), // L2 TTL
            true // single flight
    ));

    private final CacheSpec spec;

    GlobalCacheKey(CacheSpec spec) {
        this.spec = spec;
    }

    public CacheSpec spec() {
        return spec;
    }

    /** 多个 key 共享的预设规格。集中改一处。 */
    private static final class Defaults {

        static final CacheSpec CONFIG = CacheSpec.local(Duration.ofMinutes(10), 1000);

        private Defaults() {
        }
    }
}
