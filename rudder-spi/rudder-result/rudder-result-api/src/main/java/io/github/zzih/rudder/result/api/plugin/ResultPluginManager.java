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
import io.github.zzih.rudder.result.api.ResultProperties;
import io.github.zzih.rudder.result.api.spi.ResultFormatFactory;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.context.ProviderContext;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Result 插件注册表。所有 5 个 provider 共用 {@link ResultProperties},因此 ResultFormatFactory 不再带 P 泛型。 */
@Component
public class ResultPluginManager
        extends
            AbstractConfigurablePluginRegistry<ProviderContext, ResultFormatFactory> {

    /** 启动时构建的扩展名 → 默认配置 ResultFormat 缓存,按已写文件反查格式(与 active 无关)。 */
    private Map<String, ResultFormat> formatsByExtension = Map.of();

    public ResultPluginManager(ProviderContext providerContext) {
        super(ResultFormatFactory.class, providerContext, "result");
    }

    @Override
    protected void onAfterInit() {
        super.onAfterInit();
        Map<String, ResultFormat> byExt = new LinkedHashMap<>();
        ResultProperties defaults = ResultProperties.defaults();
        for (ResultFormatFactory factory : factories.values()) {
            ResultFormat format = factory.create(providerContext, defaults);
            byExt.put(format.extension(), format);
        }
        this.formatsByExtension = Map.copyOf(byExt);
    }

    /** 用 provider + JSON 参数造一个 active ResultFormat 实例。 */
    public ResultFormat create(String provider, String providerParamsJson) {
        ResultFormatFactory factory = requireFactory(provider);
        ResultProperties props = this.<ResultProperties>deserializeProps(factory, providerParamsJson);
        return factory.create(providerContext, props);
    }

    /** 按文件扩展名反查格式,供下载场景按已写文件读回。命中预构建缓存,O(扩展数)。 */
    public ResultFormat getByExtension(String path) {
        for (Map.Entry<String, ResultFormat> entry : formatsByExtension.entrySet()) {
            if (path.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("No result format found for path: " + path);
    }
}
