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

/** 验证 RAG pipeline 配置加载/保存/容错。 */
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
        // 让 cache.getOrLoad 直接调 supplier (跳过缓存,简化测试)
        when(cache.getOrLoad(eq(GlobalCacheKey.RAG_PIPELINE), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    @DisplayName("DB 没 row → 返回 defaults (active() 永不 null)")
    void active_noDbRow_returnsDefaults() {
        when(dao.selectActive()).thenReturn(null);

        RagPipelineSettings result = service.active();

        assertThat(result).isEqualTo(RagPipelineSettings.defaults());
    }

    @Test
    @DisplayName("DB row 存在但 settings 空 → 返回 defaults")
    void active_emptyParams_returnsDefaults() {
        RagPipelineConfig c = new RagPipelineConfig();
        c.setSettingsJson("");
        when(dao.selectActive()).thenReturn(c);

        assertThat(service.active()).isEqualTo(RagPipelineSettings.defaults());
    }

    @Test
    @DisplayName("正常路径: 解析 JSON 返回 RagPipelineSettings")
    void active_validJson_parsedCorrectly() {
        RagPipelineConfig c = new RagPipelineConfig();
        c.setSettingsJson("""
                {
                  "rewriteEnabled": true,
                  "multiQueryEnabled": true,
                  "multiQueryCount": 4,
                  "multiQueryIncludeOriginal": false,
                  "compressionEnabled": false,
                  "translationEnabled": false,
                  "translationTargetLanguage": "english",
                  "rerankStageEnabled": true,
                  "rerankTopN": 10,
                  "keywordEnricherEnabled": true,
                  "summaryEnricherEnabled": false,
                  "augmenterAllowEmptyContext": true
                }
                """);
        when(dao.selectActive()).thenReturn(c);

        RagPipelineSettings result = service.active();

        assertThat(result.rewriteEnabled()).isTrue();
        assertThat(result.multiQueryCount()).isEqualTo(4);
        assertThat(result.rerankTopN()).isEqualTo(10);
        assertThat(result.keywordEnricherEnabled()).isTrue();
    }

    @Test
    @DisplayName("缺字段的旧 JSON → Jackson 默认值 + record compact constructor 规范化")
    void active_partialJson_missingFieldsDefaultToFalse() {
        // 模拟老 row 只有部分字段(后续加的字段缺失)
        RagPipelineConfig c = new RagPipelineConfig();
        c.setSettingsJson("""
                {
                  "rewriteEnabled": true,
                  "multiQueryCount": 0
                }
                """);
        when(dao.selectActive()).thenReturn(c);

        RagPipelineSettings result = service.active();

        assertThat(result.rewriteEnabled()).isTrue();
        assertThat(result.multiQueryEnabled()).isFalse();
        assertThat(result.keywordEnricherEnabled()).isFalse();
        // compact constructor 把 0 normalize 成默认 3
        assertThat(result.multiQueryCount()).isEqualTo(3);
        // null targetLanguage 被规范化成 "english"
        assertThat(result.translationTargetLanguage()).isEqualTo("english");
    }

    @Test
    @DisplayName("malformed JSON → 不抛异常,降级到 defaults")
    void active_malformedJson_fallsBackToDefaults() {
        RagPipelineConfig c = new RagPipelineConfig();
        c.setSettingsJson("{not json}");
        when(dao.selectActive()).thenReturn(c);

        assertThat(service.active()).isEqualTo(RagPipelineSettings.defaults());
    }

    @Test
    @DisplayName("saveDetail: 没现有 row → 创建新行 + insert")
    void saveDetail_noExisting_inserts() {
        when(dao.selectActive()).thenReturn(null);

        RagPipelineSettings settings = new RagPipelineSettings(
                true, false, 3, true,
                false, false, "english",
                true, 5, false, false, true);
        service.saveDetail(settings);

        ArgumentCaptor<RagPipelineConfig> captor = ArgumentCaptor.forClass(RagPipelineConfig.class);
        verify(dao, times(1)).insert(captor.capture());
        RagPipelineConfig saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSettingsJson()).contains("\"rewriteEnabled\":true");
        verify(cache).invalidate(GlobalCacheKey.RAG_PIPELINE);
    }

    @Test
    @DisplayName("saveDetail: 已有 row → updateById")
    void saveDetail_existingRow_updates() {
        RagPipelineConfig existing = new RagPipelineConfig();
        existing.setId(42L);
        when(dao.selectActive()).thenReturn(existing);

        service.saveDetail(RagPipelineSettings.defaults());

        verify(dao, times(1)).updateById(any(RagPipelineConfig.class));
        verify(cache).invalidate(GlobalCacheKey.RAG_PIPELINE);
    }
}
