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

package io.github.zzih.rudder.rerank.api;

import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

/**
 * Rerank provider 契约 —— cross-encoder 模型对粗排候选做精排。
 *
 * <p>不绑 Spring AI 的 {@code DocumentPostProcessor},因为 rerank 不只 RAG advisor 用:
 * <ul>
 *   <li>RAG 链路: 由 {@code RerankDocumentPostProcessor} 桥接</li>
 *   <li>Agent 工具 / eval / debug UI 直接调本接口</li>
 * </ul>
 *
 * <p>SPI 接口**只暴露公共参数**(query/documents/topN)。provider 特有参数
 * (如 Cohere 的 {@code maxTokensPerDoc} / 阿里 native 的 {@code instruction})
 * 在各自的 Properties + Factory 里固化进 client 实例。
 */
public interface RerankClient extends AutoCloseable {

    /**
     * 给 query 和 N 个候选文档做精排。
     *
     * @param query     查询文本
     * @param documents 候选文档全文(纯字符串,调用方负责把 Spring AI {@code Document} 拆出 text)
     * @param topN      返回前几个,&lt;= 0 表示返回全部
     * @return 按 score 降序排列的 {@link RerankResult},长度 &lt;= min(documents.size(), topN)
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);

    /** 当前 provider 用的模型 id。用于审计日志、调试。 */
    String modelId();

    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    @Override
    default void close() {
    }
}
