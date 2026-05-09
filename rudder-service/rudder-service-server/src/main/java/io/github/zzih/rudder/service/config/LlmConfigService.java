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
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.plugin.LlmPluginManager;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

/** LlmClient active 实例的访问入口。 */
@Service
public class LlmConfigService extends AbstractConfigService<LlmClient> {

    private final LlmPluginManager pluginManager;

    public LlmConfigService(GlobalCacheService cache, SpiConfigDao spiConfigDao,
                            LlmPluginManager pluginManager) {
        super(cache, GlobalCacheKey.LLM, ConfigErrorCode.LLM_NOT_CONFIGURED, spiConfigDao, SpiType.LLM);
        this.pluginManager = pluginManager;
    }

    @Override
    protected AbstractConfigurablePluginRegistry<?, ?> pluginManager() {
        return pluginManager;
    }

    @Override
    protected LlmClient buildInstance(String provider, String providerParamsJson) {
        return pluginManager.create(provider, providerParamsJson);
    }

    @Override
    protected HealthStatus healthOf(LlmClient instance) {
        return instance.healthCheck();
    }

    /**
     * 拿当前 active LLM 暴露的 Spring AI {@link org.springframework.ai.chat.model.ChatModel}。
     * 任何异常 / 未配 / provider 不暴露 ChatModel 时返回 null,**不抛异常** ——
     * 调用方做"有就用没就 graceful skip"的语义(enricher / evaluator / debug 等场景)。
     */
    public org.springframework.ai.chat.model.ChatModel activeChatModel() {
        try {
            LlmClient llm = active();
            return llm == null ? null : llm.getChatModel();
        } catch (Exception e) {
            return null;
        }
    }
}
