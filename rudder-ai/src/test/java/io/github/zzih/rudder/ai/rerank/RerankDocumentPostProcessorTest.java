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

package io.github.zzih.rudder.ai.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.RerankResult;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * 桥接层测试: 验证 score 写回 metadata + 失败降级 + 边界。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RerankDocumentPostProcessorTest {

    @Mock
    private RerankClient rerankClient;

    private RerankDocumentPostProcessor processor;

    private final List<Document> docs = List.of(
            new Document("Doc A — Flink Window API"),
            new Document("Doc B — Flink State Management"),
            new Document("Doc C — Watermark and Late Data"));

    @BeforeEach
    void setUp() {
        processor = new RerankDocumentPostProcessor(rerankClient, 5);
        when(rerankClient.modelId()).thenReturn("rerank-v3.5");
    }

    @Test
    @DisplayName("正常路径: 按 rerank 返回顺序输出 + score/model 写到 metadata")
    void process_reordersDocumentsAndWritesScore() {
        when(rerankClient.rerank(eq("flink late data"), any(), eq(5)))
                .thenReturn(List.of(
                        new RerankResult(2, 0.94),
                        new RerankResult(0, 0.61),
                        new RerankResult(1, 0.12)));

        Query query = Query.builder().text("flink late data").build();
        List<Document> result = processor.process(query, docs);

        assertThat(result).hasSize(3);
        // 第 1 名是原 docs[2]
        assertThat(result.get(0).getText()).contains("Watermark");
        assertThat(result.get(0).getMetadata())
                .containsEntry(RerankDocumentPostProcessor.META_RERANK_SCORE, 0.94)
                .containsEntry(RerankDocumentPostProcessor.META_RERANK_MODEL, "rerank-v3.5");
        // 第 2 名是原 docs[0]
        assertThat(result.get(1).getText()).contains("Window API");
    }

    @Test
    @DisplayName("rerank 抛异常 → 退回原 list, 不抛错给 advisor")
    void process_swallowsExceptionAndReturnsOriginal() {
        when(rerankClient.rerank(anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("provider down"));

        Query query = Query.builder().text("q").build();
        List<Document> result = processor.process(query, docs);

        assertThat(result).isSameAs(docs);
    }

    @Test
    @DisplayName("rerank 返回空 list → 退回原 list (避免误删所有候选)")
    void process_emptyResultReturnsOriginal() {
        when(rerankClient.rerank(anyString(), any(), anyInt())).thenReturn(List.of());

        List<Document> result = processor.process(Query.builder().text("q").build(), docs);
        assertThat(result).isSameAs(docs);
    }

    @Test
    @DisplayName("rerank 返回越界 index → 跳过该项, 不抛错")
    void process_outOfBoundsIndexSkipped() {
        when(rerankClient.rerank(anyString(), any(), anyInt()))
                .thenReturn(List.of(
                        new RerankResult(99, 0.99), // 越界
                        new RerankResult(0, 0.5)));

        List<Document> result = processor.process(Query.builder().text("q").build(), docs);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).contains("Window API");
    }

    @Test
    @DisplayName("空 documents 直接返回")
    void process_emptyDocsReturnsEmpty() {
        List<Document> result = processor.process(Query.builder().text("q").build(), List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("query 为 null → 直接返回原 list")
    void process_nullQueryReturnsOriginal() {
        List<Document> result = processor.process(null, docs);
        assertThat(result).isSameAs(docs);
    }
}
