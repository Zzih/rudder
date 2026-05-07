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

package io.github.zzih.rudder.result.api.plugin;

import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.spi.ResultFormatFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Result 插件注册表。**只暴露工厂能力**（create / getByExtension），不持 active 状态。
 * 当前生效的 ResultFormat 由上层 {@code ResultConfigService} 通过
 * {@link io.github.zzih.rudder.cache.GlobalCacheService} 管理。
 */
@Component
public class ResultPluginManager extends AbstractConfigurablePluginRegistry<ProviderContext, ResultFormatFactory> {

    /** 启动时构建的扩展名 → 默认配置 ResultFormat 缓存，按已写文件反查格式（与 active 无关）。 */
    private Map<String, ResultFormat> formatsByExtension = Map.of();

    public ResultPluginManager(ProviderContext providerContext) {
        super(ResultFormatFactory.class, providerContext, "result");
    }

    @Override
    protected void onAfterInit() {
        super.onAfterInit();
        Map<String, ResultFormat> byExt = new LinkedHashMap<>();
        for (ResultFormatFactory factory : factories.values()) {
            ResultFormat format = factory.create(providerContext, Map.of());
            byExt.put(format.extension(), format);
        }
        this.formatsByExtension = Map.copyOf(byExt);
    }

    /** 用 provider + 配置造一个 ResultFormat 实例。无状态，纯工厂方法。 */
    public ResultFormat create(String provider, Map<String, String> config) {
        ResultFormatFactory factory = requireFactory(provider);
        Map<String, String> merged = new HashMap<>(config != null ? config : Map.of());
        return factory.create(providerContext, merged);
    }

    /** 按文件扩展名反查格式，供下载场景按已写文件读回。命中预构建缓存，O(扩展数)。 */
    public ResultFormat getByExtension(String path) {
        for (Map.Entry<String, ResultFormat> entry : formatsByExtension.entrySet()) {
            if (path.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("No result format found for path: " + path);
    }
}
