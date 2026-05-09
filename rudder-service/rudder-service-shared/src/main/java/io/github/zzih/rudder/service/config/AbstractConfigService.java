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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.service.config.dto.ProviderConfigDTO;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * SPI ConfigService 公共骨架。{@link SpiConfig} 表按 {@link #spiType} 区分,
 * per (type, provider) 一行由 {@code uk_type_provider} 保证。
 *
 * @param <T> active 实例类型
 */
@Slf4j
public abstract class AbstractConfigService<T> {

    private final GlobalCacheService cache;
    private final GlobalCacheKey cacheKey;
    private final ConfigErrorCode notConfiguredCode;
    private final SpiConfigDao spiConfigDao;
    private final SpiType spiType;

    protected AbstractConfigService(GlobalCacheService cache, GlobalCacheKey cacheKey,
                                    ConfigErrorCode notConfiguredCode,
                                    SpiConfigDao spiConfigDao, SpiType spiType) {
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.notConfiguredCode = notConfiguredCode;
        this.spiConfigDao = spiConfigDao;
        this.spiType = spiType;
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

    public HealthStatus health() {
        T t = active();
        return t == null ? HealthStatus.unknown() : healthOf(t);
    }

    public String activeProvider() {
        SpiConfig c = spiConfigDao.selectActive(spiType);
        return c != null ? c.getProvider() : null;
    }

    public ProviderConfigDTO getActiveDetail() {
        return toDto(spiConfigDao.selectActive(spiType));
    }

    public List<ProviderConfigDTO> listAll() {
        return spiConfigDao.selectAllByType(spiType).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void saveDetail(ProviderConfigDTO body) {
        Object raw = body.getProviderParams();
        String inputJson = raw == null ? null : (raw instanceof String s ? s : JsonUtils.toJson(raw));
        String canonical = pluginManager().validateAndCanonicalize(body.getProvider(), inputJson);

        List<SpiConfig> rows = spiConfigDao.selectAllByType(spiType);
        String previousProvider = rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(SpiConfig::getProvider)
                .findFirst()
                .orElse(null);
        SpiConfig c = rows.stream()
                .filter(r -> body.getProvider().equalsIgnoreCase(r.getProvider()))
                .findFirst()
                .orElseGet(() -> {
                    SpiConfig fresh = new SpiConfig();
                    fresh.setType(spiType);
                    fresh.setProvider(body.getProvider());
                    return fresh;
                });
        c.setProviderParams(canonical);
        boolean enabled = body.getEnabled() == null || body.getEnabled();
        c.setEnabled(enabled);
        if (c.getId() != null) {
            spiConfigDao.updateById(c);
        } else {
            spiConfigDao.insert(c);
        }
        if (enabled) {
            spiConfigDao.disableOthers(spiType, body.getProvider());
        }
        // afterCommit:否则远端节点收到广播立即 build 会读到 commit 前的 DB,把旧实例缓存到 TTL 过期。
        runAfterCommit(() -> {
            cache.invalidate(cacheKey);
            onProviderChanged(previousProvider, body.getProvider());
        });
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private T build() {
        SpiConfig c = spiConfigDao.selectActive(spiType);
        if (c == null) {
            String fb = fallbackProvider();
            return fb == null ? null : buildInstance(fb, "{}");
        }
        return buildInstance(c.getProvider(), c.getProviderParams());
    }

    /** 反序列化失败兜底为 null:保留行让 admin 看到坏数据并修复,而不是整个 listAll 直接抛。 */
    private ProviderConfigDTO toDto(SpiConfig c) {
        if (c == null) {
            return null;
        }
        ProviderConfigDTO dto = new ProviderConfigDTO();
        dto.setId(c.getId());
        dto.setProvider(c.getProvider());
        dto.setEnabled(c.getEnabled());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        try {
            dto.setProviderParams(pluginManager().deserialize(c.getProvider(), c.getProviderParams()));
        } catch (Exception e) {
            log.warn("Failed to deserialize providerParams for {}.{}: {}",
                    spiType, c.getProvider(), e.getMessage());
            dto.setProviderParams(null);
        }
        return dto;
    }

    protected abstract AbstractConfigurablePluginRegistry<?, ?> pluginManager();

    protected abstract T buildInstance(String provider, String providerParamsJson);

    protected abstract HealthStatus healthOf(T instance);

    /** 切换 provider 时的额外清理。默认 no-op;Runtime override 关 SDK 资源。 */
    protected void onProviderChanged(String previousProvider, String newProvider) {
    }

    /** active 行不存在时的 fallback provider key。默认无 fallback;Approval/Metadata override。 */
    protected String fallbackProvider() {
        return null;
    }
}
