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

package io.github.zzih.rudder.embedding.api;

import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

/** Embedding provider 契约(DashScope / OpenAI / 本地 bge 等)。 */
public interface EmbeddingClient extends AutoCloseable {

    /** 向量化单条文本。 */
    default float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /** 批量(通常一次可到几十/几百条)。 */
    List<float[]> embedBatch(List<String> texts);

    /** 向量维度 —— 用于初始化 Qdrant collection。 */
    int dimensions();

    /** 当前 provider 用的模型 id。 */
    String modelId();

    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    @Override
    default void close() {
    }
}
