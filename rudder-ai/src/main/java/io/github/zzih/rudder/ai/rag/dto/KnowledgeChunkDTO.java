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
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;

import lombok.Data;

/** MCP knowledge.search 返回的检索片段 DTO。把 {@link RetrievedChunk}(含 service 内部字段)翻译成对外稳定契约。 */
@Data
public class KnowledgeChunkDTO {

    private Long documentId;
    private String title;
    private String chunkText;
    private String docType;
    private float score;

    public static KnowledgeChunkDTO from(RetrievedChunk chunk) {
        return BeanConvertUtils.convert(chunk, KnowledgeChunkDTO.class);
    }
}
