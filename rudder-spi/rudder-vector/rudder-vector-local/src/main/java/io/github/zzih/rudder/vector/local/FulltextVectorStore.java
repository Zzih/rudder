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

package io.github.zzih.rudder.vector.local;

import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.vector.api.VectorPoint;
import io.github.zzih.rudder.vector.api.VectorQuery;
import io.github.zzih.rudder.vector.api.VectorSearchHit;
import io.github.zzih.rudder.vector.api.VectorStore;

import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * FULLTEXT 降级实现。
 * <p>
 * 未配 Qdrant 时兜底。向量操作全部 no-op(文档+chunk 仍存在 t_r_ai_document 的 FULLTEXT 索引里,
 * DocumentRetrievalService 发现是 FulltextVectorStore 时切换成 MySQL MATCH AGAINST 查询)。
 * <p>
 * search() 返回空是契约:调用方据此触发 FULLTEXT fallback 路径。
 */
@Slf4j
public class FulltextVectorStore implements VectorStore {

    @Override
    public void ensureCollection(String collection, int dimensions) {
        log.debug("FulltextVectorStore: ensureCollection no-op ({})", collection);
    }

    @Override
    public void upsert(String collection, Collection<VectorPoint> points) {
        log.debug("FulltextVectorStore: upsert no-op ({} points)", points == null ? 0 : points.size());
    }

    @Override
    public void deleteByIds(String collection, Collection<String> pointIds) {
    }

    @Override
    public void deleteByPayload(String collection, String payloadKey, Object payloadValue) {
    }

    @Override
    public List<VectorSearchHit> search(VectorQuery query) {
        return List.of();
    }

    @Override
    public HealthStatus healthCheck() {
        return HealthStatus.healthy();
    }

    @Override
    public boolean supportsVectors() {
        return false;
    }
}
