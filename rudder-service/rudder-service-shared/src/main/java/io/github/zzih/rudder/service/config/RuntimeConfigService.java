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
import io.github.zzih.rudder.dao.dao.RuntimeConfigDao;
import io.github.zzih.rudder.dao.entity.RuntimeConfig;
import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.TaskFactory;
import io.github.zzih.rudder.runtime.api.plugin.RuntimePluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Runtime active 实例的访问入口。全局唯一 active EngineRuntime,
 * 通过 {@link GlobalCacheService} 缓存,DB 改动 → invalidate → 下次 build。
 */
@Service
@RequiredArgsConstructor
public class RuntimeConfigService {

    private final GlobalCacheService cache;
    private final RuntimeConfigDao dao;
    private final RuntimePluginManager pluginManager;

    /** 当前 active runtime,未配置返回 null。 */
    public EngineRuntime active() {
        return cache.getOrLoad(GlobalCacheKey.RUNTIME, this::build);
    }

    /** 当前 active runtime,未配置抛 {@link ConfigErrorCode#RUNTIME_NOT_CONFIGURED}。 */
    public EngineRuntime required() {
        EngineRuntime r = active();
        if (r == null) {
            throw new BizException(ConfigErrorCode.RUNTIME_NOT_CONFIGURED);
        }
        return r;
    }

    /** Active provider key,未配置返回 null。 */
    public String activeProvider() {
        EngineRuntime r = active();
        return r != null ? r.provider() : null;
    }

    /** Active runtime 注入的环境变量。未配置返回空 map。 */
    public Map<String, String> envVars() {
        EngineRuntime r = active();
        return r != null ? r.envVars() : Map.of();
    }

    /** 该 TaskType 是否被 active runtime 接管(返回云上子类工厂)。empty = Worker 走 channel 默认。 */
    public Optional<TaskFactory> taskFactoryFor(TaskType type) {
        EngineRuntime r = active();
        return r != null ? r.taskFactoryFor(type) : Optional.empty();
    }

    public void save(RuntimeConfig config) {
        // 提前从 DB 查出旧 provider;不能从 cache 读,否则 TTL 过期或并发 invalidate 会拿不到。
        RuntimeConfig previous = dao.selectActive();
        String previousProvider = previous != null ? previous.getProvider() : null;
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
        // 立即生效:invalidate cache,下次 active() 调用 build() 拿新 runtime。
        cache.invalidate(GlobalCacheKey.RUNTIME);
        // 仅当切换到不同 provider 时关掉旧 provider 的共享资源(同 provider 仅改参数,资源仍在用)。
        if (previousProvider != null && !previousProvider.equalsIgnoreCase(config.getProvider())) {
            pluginManager.closeResources(previousProvider);
        }
    }

    public HealthStatus health() {
        EngineRuntime r = active();
        return r != null ? r.healthCheck() : HealthStatus.unknown();
    }

    private EngineRuntime build() {
        RuntimeConfig c = dao.selectActive();
        if (c == null || !Boolean.TRUE.equals(c.getEnabled()) || c.getProvider() == null) {
            return null;
        }
        Map<String, String> params = JsonUtils.toMap(c.getProviderParams());
        return pluginManager.create(c.getProvider(), params);
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ProviderConfigDTO body) {
        RuntimeConfig c = dao.selectActive();
        if (c == null) {
            c = new RuntimeConfig();
        }
        c.setProvider(body.getProvider());
        c.setProviderParams(body.getProviderParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }
}
