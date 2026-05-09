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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.RagPipelineConfigDao;
import io.github.zzih.rudder.dao.entity.RagPipelineConfig;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 链路配置（{@code t_r_rag_pipeline_config} 单 row）。
 *
 * <p>不是 SPI 选型——承载 chunk size / topK / reranker enable 等参数；脱离 AbstractConfigService 基类自管缓存。
 *
 * <p>{@link #active()} 永远返回非 null：DB 没配 / JSON 解析失败时 fallback 到
 * {@link RagPipelineSettings#defaults()}，缓存正常生效，上游 {@code ChatClientFactory} 不用判空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipelineConfigService {

    private final GlobalCacheService cache;
    private final RagPipelineConfigDao dao;

    public RagPipelineSettings active() {
        return cache.getOrLoad(GlobalCacheKey.RAG_PIPELINE, this::build);
    }

    public HealthStatus health() {
        return HealthStatus.healthy();
    }

    public void saveDetail(RagPipelineSettings settings) {
        RagPipelineConfig c = dao.selectActive();
        if (c == null) {
            c = new RagPipelineConfig();
        }
        c.setSettingsJson(JsonUtils.toJson(settings));
        c.setEnabled(true);
        if (c.getId() != null) {
            dao.updateById(c);
        } else {
            dao.insert(c);
        }
        cache.invalidate(GlobalCacheKey.RAG_PIPELINE);
    }

    private RagPipelineSettings build() {
        RagPipelineConfig c = dao.selectActive();
        if (c == null || c.getSettingsJson() == null || c.getSettingsJson().isBlank()) {
            return RagPipelineSettings.defaults();
        }
        try {
            return JsonUtils.fromJson(c.getSettingsJson(), RagPipelineSettings.class);
        } catch (Exception e) {
            log.warn("RAG pipeline config malformed JSON, falling back to defaults: {}", e.getMessage());
            return RagPipelineSettings.defaults();
        }
    }
}
