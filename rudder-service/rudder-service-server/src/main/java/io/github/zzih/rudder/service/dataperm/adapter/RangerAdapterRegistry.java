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

package io.github.zzih.rudder.service.dataperm.adapter;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.dataperm.config.PluginType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 按 {@link PluginType} 查 {@link RangerResourceAdapter}。Spring 自动注入所有 adapter,启动时建索引。
 * {@link #find} 返 {@link Optional#empty()} 供 UI 判断是否支持权限管控;
 * {@link #require} 抛 {@code RANGER_SERVICE_NOT_FOUND},Reconciler 路径触发即脏数据。
 */
@Slf4j
@Component
public class RangerAdapterRegistry {

    private final Map<PluginType, RangerResourceAdapter> byType;

    public RangerAdapterRegistry(List<RangerResourceAdapter> adapters) {
        Map<PluginType, RangerResourceAdapter> map = new EnumMap<>(PluginType.class);
        for (RangerResourceAdapter a : adapters) {
            PluginType key = a.supportedPluginType();
            RangerResourceAdapter prev = map.put(key, a);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate RangerResourceAdapter for pluginType=" + key
                                + ": " + prev.getClass().getName() + " vs " + a.getClass().getName());
            }
        }
        this.byType = Map.copyOf(map);
        log.info("RangerAdapterRegistry initialized with {} plugin types: {}", byType.size(), byType.keySet());
    }

    public Optional<RangerResourceAdapter> find(PluginType pluginType) {
        if (pluginType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byType.get(pluginType));
    }

    public RangerResourceAdapter require(PluginType pluginType) {
        return find(pluginType).orElseThrow(() -> new BizException(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND,
                "no adapter for pluginType=" + pluginType));
    }

    /** 当前已注册的 plugin type 集合,供 UI 渲染"哪些 plugin 可配权限"。 */
    public Set<PluginType> registeredTypes() {
        return byType.keySet();
    }
}
