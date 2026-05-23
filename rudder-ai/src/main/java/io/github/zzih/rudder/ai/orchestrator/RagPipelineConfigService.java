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

import io.github.zzih.rudder.ai.orchestrator.dto.RagPipelineConfigDTO;
import io.github.zzih.rudder.dao.dao.RagPipelineConfigDao;
import io.github.zzih.rudder.dao.entity.RagPipelineConfig;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** RAG 链路配置服务({@code t_r_rag_pipeline_config} 单 row,12 标量列打平)。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipelineConfigService {

    private final GlobalCacheService cache;
    private final RagPipelineConfigDao dao;

    public RagPipelineConfigDTO active() {
        return cache.getOrLoad(GlobalCacheKey.RAG_PIPELINE, this::build);
    }

    public HealthStatus health() {
        return HealthStatus.healthy();
    }

    public void saveDetail(RagPipelineConfigDTO settings) {
        RagPipelineConfig c = Optional.ofNullable(dao.selectActive()).orElseGet(RagPipelineConfig::new);
        BeanUtils.copyProperties(settings, c);
        if (c.getId() == null) {
            dao.insert(c);
        } else {
            dao.updateById(c);
        }
        cache.invalidate(GlobalCacheKey.RAG_PIPELINE);
    }

    private RagPipelineConfigDTO build() {
        RagPipelineConfig c = dao.selectActive();
        if (c == null) {
            return defaultsDto();
        }
        RagPipelineConfigDTO dto = new RagPipelineConfigDTO();
        BeanUtils.copyProperties(c, dto);
        return dto;
    }

    private static RagPipelineConfigDTO defaultsDto() {
        RagPipelineConfigDTO d = new RagPipelineConfigDTO();
        d.setRewriteEnabled(false);
        d.setMultiQueryEnabled(false);
        d.setMultiQueryCount(3);
        d.setMultiQueryIncludeOriginal(true);
        d.setCompressionEnabled(false);
        d.setTranslationEnabled(false);
        d.setTranslationTargetLanguage("english");
        d.setRerankStageEnabled(false);
        d.setRerankTopN(5);
        d.setKeywordEnricherEnabled(false);
        d.setSummaryEnricherEnabled(false);
        d.setAugmenterAllowEmptyContext(true);
        return d;
    }
}
