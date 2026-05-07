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

package io.github.zzih.rudder.ai.orchestrator;

import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiConfigDao;
import io.github.zzih.rudder.dao.entity.AiConfig;
import io.github.zzih.rudder.dao.enums.AiConfigType;
import io.github.zzih.rudder.service.config.AbstractConfigService;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * RAG 链路配置(单例 row in {@code t_r_ai_config}, type=RAG_PIPELINE)。
 *
 * <p>跟其它 *ConfigService 不同,本服务**永远返回非 null** —— DB 没配时
 * {@link #build} 直接返回 {@link RagPipelineSettings#defaults()},缓存正常生效,
 * 上游 {@code ChatClientFactory} 不用判空。
 */
@Slf4j
@Service
public class RagPipelineConfigService extends AbstractConfigService<AiConfig, RagPipelineSettings> {

    private static final String DEFAULT_PROVIDER = "DEFAULT";

    private final AiConfigDao dao;

    public RagPipelineConfigService(GlobalCacheService cache, AiConfigDao dao) {
        // 占位错误码 —— 本服务 build() 永不返回 null,never required(),错误码不会被触发
        super(cache, GlobalCacheKey.RAG_PIPELINE, ConfigErrorCode.LLM_NOT_CONFIGURED);
        this.dao = dao;
    }

    @Override
    protected RagPipelineSettings build() {
        AiConfig c = dao.selectActive(AiConfigType.RAG_PIPELINE);
        if (c == null || c.getProviderParams() == null || c.getProviderParams().isBlank()) {
            return RagPipelineSettings.defaults();
        }
        try {
            return JsonUtils.fromJson(c.getProviderParams(), RagPipelineSettings.class);
        } catch (Exception e) {
            log.warn("RAG pipeline config malformed JSON, falling back to defaults: {}", e.getMessage());
            return RagPipelineSettings.defaults();
        }
    }

    @Override
    protected void doUpsert(AiConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
    }

    @Override
    protected HealthStatus healthOf(RagPipelineSettings instance) {
        return HealthStatus.healthy();
    }

    /**
     * Admin UI 保存入口。
     */
    public void saveDetail(RagPipelineSettings settings) {
        AiConfig c = dao.selectActive(AiConfigType.RAG_PIPELINE);
        if (c == null) {
            c = new AiConfig();
            c.setType(AiConfigType.RAG_PIPELINE);
            c.setProvider(DEFAULT_PROVIDER);
        }
        c.setProviderParams(JsonUtils.toJson(settings));
        c.setEnabled(true);
        save(c);
    }
}
