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

import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Collection;
import java.util.List;

/**
 * 向量存储契约。实现:
 * <ul>
 *   <li>rudder-vector-qdrant —— 首选,完整语义召回</li>
 *   <li>rudder-vector-local —— 无 Qdrant 时兜底,走 MySQL FULLTEXT(nearest 仍可调用但忽略向量,纯关键词)</li>
 * </ul>
 * <p>
 * collection 命名约定:`{workspaceId}_{docType}`,同 workspace 内不同 docType 分区。
 */
public interface VectorStore extends AutoCloseable {

    /** 确保 collection 存在(维度不匹配时重建/抛错,由具体实现决定)。 */
    void ensureCollection(String collection, int dimensions);

    /** 批量写入/覆盖。point id 必须由调用方决定(建议 UUID)。 */
    void upsert(String collection, Collection<VectorPoint> points);

    /** 按 id 批量删除。 */
    void deleteByIds(String collection, Collection<String> pointIds);

    /** 按 payload 字段删除(例:按 documentId 清掉所有 chunk)。 */
    void deleteByPayload(String collection, String payloadKey, Object payloadValue);

    /** 语义搜索(Qdrant:cosine 相似度;local:ignore queryVector 走 FULLTEXT)。 */
    List<VectorSearchHit> search(VectorQuery query);

    /** 健康检查。SPI 默认返回 unknown();provider 视情况覆盖。 */
    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    /**
     * 实现是否真的执行向量检索。Qdrant 返回 true;FULLTEXT 降级返回 false。
     * 调用方据此决定是否调 embedding、是否写 embedding 元信息表(避免向量没存但元信息却有)。
     */
    default boolean supportsVectors() {
        return true;
    }

    @Override
    default void close() {
        // default no-op;持有连接 / HTTP client 的实现 override
    }
}
