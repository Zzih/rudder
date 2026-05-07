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

package io.github.zzih.rudder.vector.api;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/** 召回请求。 */
@Data
@Builder
public class VectorQuery {

    private String collection;

    /** 查询向量。local 实现会忽略。 */
    private float[] queryVector;

    /**
     * 原始查询文本。Qdrant 实现可选用于 rerank;local 实现作为 FULLTEXT MATCH 表达式。
     * 任何实现都应接受此字段,方便调用方复用同一 VectorQuery 对象跨 impl。
     */
    private String queryText;

    /** 返回前 K 条。 */
    @Builder.Default
    private int topK = 10;

    /** 最低相似度阈值(0~1)。local 实现忽略。 */
    private Float minScore;

    /** payload 精确匹配过滤(例: {"workspaceId": 1, "docType": "SCRIPT"})。 */
    private Map<String, Object> payloadFilter;
}
