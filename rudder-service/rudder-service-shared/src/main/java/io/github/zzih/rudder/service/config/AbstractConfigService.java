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

package io.github.zzih.rudder.service.config;

import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

/**
 * 公共骨架：active / required / save / health。子类覆盖 {@link #build} / {@link #doUpsert} /
 * {@link #healthOf} 即可。
 *
 * <p>Runtime / Approval / Metadata 这三个有特殊语义（closeResources、Active record、fallback），
 * 不走本基类。
 *
 * @param <E> 配置实体类型（FileConfig / AiConfig 等）
 * @param <T> active 实例类型（FileStorage / LlmClient 等）
 */
public abstract class AbstractConfigService<E, T> {

    private final GlobalCacheService cache;
    private final GlobalCacheKey cacheKey;
    private final ConfigErrorCode notConfiguredCode;

    protected AbstractConfigService(GlobalCacheService cache, GlobalCacheKey cacheKey,
                                    ConfigErrorCode notConfiguredCode) {
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.notConfiguredCode = notConfiguredCode;
    }

    public T active() {
        return cache.getOrLoad(cacheKey, this::build);
    }

    public T required() {
        T t = active();
        if (t == null) {
            throw new BizException(notConfiguredCode);
        }
        return t;
    }

    public void save(E config) {
        doUpsert(config);
        cache.invalidate(cacheKey);
    }

    public HealthStatus health() {
        T t = active();
        return t == null ? HealthStatus.unknown() : healthOf(t);
    }

    /** Cache miss 时回源：DB 拉 active 配置 → 通过 plugin 工厂造实例。返回 null 表示未配置。 */
    protected abstract T build();

    /** 持久化：插入或按 id 更新。基类负责后续 cache invalidate + 跨节点广播。 */
    protected abstract void doUpsert(E config);

    /** 提取 active 实例的健康状态。子类一般返回 {@code instance.healthCheck()}。 */
    protected abstract HealthStatus healthOf(T instance);
}
