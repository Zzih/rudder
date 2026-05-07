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

package io.github.zzih.rudder.ai.rag.dto;

import io.github.zzih.rudder.ai.rag.DocumentRetrievalService.RetrievedChunk;

/**
 * MCP knowledge.search 返回的检索片段 DTO。
 *
 * <p>把 {@link RetrievedChunk}（service 内部类，含 score 等内部字段）翻译成对外稳定契约：
 * 调用方只关心命中文档的 id / 标题 / 片段正文 / 类型 / 相关度评分。
 */
public record KnowledgeChunkDTO(
        Long documentId,
        String title,
        String chunkText,
        String docType,
        float score) {

    public static KnowledgeChunkDTO from(RetrievedChunk chunk) {
        return new KnowledgeChunkDTO(
                chunk.getDocumentId(),
                chunk.getTitle(),
                chunk.getChunkText(),
                chunk.getDocType(),
                chunk.getScore());
    }
}
