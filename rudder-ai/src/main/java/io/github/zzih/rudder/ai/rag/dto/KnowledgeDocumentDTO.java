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

import io.github.zzih.rudder.dao.entity.AiDocument;

import java.time.LocalDateTime;

/**
 * 知识库文档元信息(不含 content),用于 list 浏览。content 单独通过 {@code knowledge.get_document}
 * 取，避免 list 接口把全文都拉回来浪费 token。
 */
public record KnowledgeDocumentDTO(
        Long id,
        String docType,
        String engineType,
        String title,
        String description,
        String sourceRef,
        LocalDateTime indexedAt,
        LocalDateTime updatedAt) {

    public static KnowledgeDocumentDTO from(AiDocument doc) {
        return new KnowledgeDocumentDTO(
                doc.getId(),
                doc.getDocType(),
                doc.getEngineType(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getSourceRef(),
                doc.getIndexedAt(),
                doc.getUpdatedAt());
    }
}
