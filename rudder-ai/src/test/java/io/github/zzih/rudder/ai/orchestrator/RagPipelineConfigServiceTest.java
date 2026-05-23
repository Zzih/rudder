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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.ai.orchestrator.dto.RagPipelineConfigDTO;
import io.github.zzih.rudder.dao.dao.RagPipelineConfigDao;
import io.github.zzih.rudder.dao.entity.RagPipelineConfig;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 验证 RAG pipeline 配置加载/保存。打平后字段从 DB 列直接映射,无 JSON 反序列化路径。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagPipelineConfigServiceTest {

    @Mock
    private GlobalCacheService cache;

    @Mock
    private RagPipelineConfigDao dao;

    private RagPipelineConfigService service;

    @BeforeEach
    void setUp() {
        service = new RagPipelineConfigService(cache, dao);
        when(cache.getOrLoad(eq(GlobalCacheKey.RAG_PIPELINE), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    @DisplayName("DB 无 row → 返回 defaults(全 false / 默认 int)")
    void active_noDbRow_returnsDefaults() {
        when(dao.selectActive()).thenReturn(null);

        RagPipelineConfigDTO result = service.active();

        assertThat(result.getRewriteEnabled()).isFalse();
        assertThat(result.getMultiQueryCount()).isEqualTo(3);
        assertThat(result.getRerankTopN()).isEqualTo(5);
        assertThat(result.getTranslationTargetLanguage()).isEqualTo("english");
    }

    @Test
    @DisplayName("DB row 字段齐全 → 按字段映射")
    void active_validRow_mapsAllFields() {
        RagPipelineConfig c = new RagPipelineConfig();
        c.setRewriteEnabled(true);
        c.setMultiQueryEnabled(true);
        c.setMultiQueryCount(4);
        c.setMultiQueryIncludeOriginal(false);
        c.setCompressionEnabled(false);
        c.setTranslationEnabled(false);
        c.setTranslationTargetLanguage("english");
        c.setRerankStageEnabled(true);
        c.setRerankTopN(10);
        c.setKeywordEnricherEnabled(true);
        c.setSummaryEnricherEnabled(false);
        c.setAugmenterAllowEmptyContext(true);
        when(dao.selectActive()).thenReturn(c);

        RagPipelineConfigDTO result = service.active();

        assertThat(result.getRewriteEnabled()).isTrue();
        assertThat(result.getMultiQueryCount()).isEqualTo(4);
        assertThat(result.getRerankTopN()).isEqualTo(10);
        assertThat(result.getKeywordEnricherEnabled()).isTrue();
        assertThat(result.getTranslationTargetLanguage()).isEqualTo("english");
    }

    @Test
    @DisplayName("saveDetail: 没现有 row → insert 新行,字段被赋值")
    void saveDetail_noExisting_inserts() {
        when(dao.selectActive()).thenReturn(null);

        RagPipelineConfigDTO settings = new RagPipelineConfigDTO();
        settings.setRewriteEnabled(true);
        settings.setRerankStageEnabled(true);
        settings.setMultiQueryCount(3);
        service.saveDetail(settings);

        ArgumentCaptor<RagPipelineConfig> captor = ArgumentCaptor.forClass(RagPipelineConfig.class);
        verify(dao, times(1)).insert(captor.capture());
        RagPipelineConfig saved = captor.getValue();
        assertThat(saved.getRewriteEnabled()).isTrue();
        assertThat(saved.getRerankStageEnabled()).isTrue();
        assertThat(saved.getMultiQueryCount()).isEqualTo(3);
        verify(cache).invalidate(GlobalCacheKey.RAG_PIPELINE);
    }

    @Test
    @DisplayName("saveDetail: 已有 row → updateById")
    void saveDetail_existingRow_updates() {
        RagPipelineConfig existing = new RagPipelineConfig();
        existing.setId(42L);
        when(dao.selectActive()).thenReturn(existing);

        service.saveDetail(new RagPipelineConfigDTO());

        verify(dao, times(1)).updateById(any(RagPipelineConfig.class));
        verify(cache).invalidate(GlobalCacheKey.RAG_PIPELINE);
    }
}
