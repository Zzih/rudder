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

package io.github.zzih.rudder.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.ai.rag.DocumentRetrievalService.RetrievedChunk;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * 关键风险点回归: ChatClient.advisors().param() → Query.context() → retriever 提参的整条链路。
 * 这条链路任何一个 key/类型不一致都会导致迁移后 RAG 静默失效。
 */
@ExtendWith(MockitoExtension.class)
class RudderDocumentRetrieverTest {

    @Mock
    private DocumentRetrievalService retrieval;

    private RudderDocumentRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new RudderDocumentRetriever(retrieval);
    }

    @Test
    @DisplayName("workspaceId 缺失时直接返回空 list,不触发底层 retrieval")
    void retrieve_missingWorkspaceId_skipsRetrieval() {
        Query query = Query.builder().text("hello").context(Map.of()).build();

        List<Document> docs = retriever.retrieve(query);

        assertThat(docs).isEmpty();
        verify(retrieval, never()).retrieve(any(), any(), anyString(), any(), Mockito_anyInt());
    }

    @Test
    @DisplayName("正常路径: ctx 全字段透传到 DocumentRetrievalService.retrieve")
    void retrieve_passesAllContextParamsThrough() {
        when(retrieval.retrieve(eq(42L), eq("WIKI"), eq("flink window"), any(), eq(7)))
                .thenReturn(List.of(new RetrievedChunk(101L, "Flink Tumbling Window",
                        "Tumbling windows are non-overlapping…", 0.83f, "WIKI")));

        Query query = Query.builder()
                .text("flink window")
                .context(Map.of(
                        RudderDocumentRetriever.CTX_WORKSPACE_ID, 42L,
                        RudderDocumentRetriever.CTX_DOC_TYPE, "WIKI",
                        RudderDocumentRetriever.CTX_TOP_K, 7,
                        RudderDocumentRetriever.CTX_ENGINE_TYPES, List.of("FLINK")))
                .build();

        List<Document> docs = retriever.retrieve(query);

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);
        assertThat(doc.getId()).isEqualTo("101");
        assertThat(doc.getText()).contains("Tumbling");
        assertThat(doc.getMetadata())
                .containsEntry("title", "Flink Tumbling Window")
                .containsEntry("docType", "WIKI")
                .containsEntry("documentId", 101L);
        assertThat(doc.getScore()).isEqualTo(0.83, org.assertj.core.data.Offset.offset(0.001));
        verify(retrieval, times(1)).retrieve(eq(42L), eq("WIKI"), eq("flink window"),
                any(Collection.class), eq(7));
    }

    @Test
    @DisplayName("workspaceId 是 Integer 时也能正确转 Long")
    void retrieve_acceptsIntegerWorkspaceId() {
        when(retrieval.retrieve(eq(42L), eq(null), anyString(), any(), Mockito_anyInt()))
                .thenReturn(List.of());

        Query query = Query.builder()
                .text("q")
                .context(Map.of(RudderDocumentRetriever.CTX_WORKSPACE_ID, 42))
                .build();

        retriever.retrieve(query);

        verify(retrieval, times(1)).retrieve(eq(42L), eq(null), eq("q"), any(), Mockito_anyInt());
    }

    @Test
    @DisplayName("retrieval 抛异常时优雅降级,返回空 list 不阻断主对话")
    void retrieve_swallowsDownstreamException() {
        when(retrieval.retrieve(any(), any(), anyString(), any(), Mockito_anyInt()))
                .thenThrow(new RuntimeException("vector store down"));

        Query query = Query.builder()
                .text("q")
                .context(Map.of(RudderDocumentRetriever.CTX_WORKSPACE_ID, 1L))
                .build();

        List<Document> docs = retriever.retrieve(query);

        assertThat(docs).isEmpty();
    }

    private static int Mockito_anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
