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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.AiDocumentEmbeddingDao;
import io.github.zzih.rudder.dao.entity.AiDocumentEmbedding;
import io.github.zzih.rudder.dao.mapper.AiDocumentEmbeddingMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiDocumentEmbeddingDaoImpl implements AiDocumentEmbeddingDao {

    private final AiDocumentEmbeddingMapper mapper;

    @Override
    public List<AiDocumentEmbedding> selectByDocument(Long documentId) {
        return mapper.selectByDocument(documentId);
    }

    @Override
    public List<AiDocumentEmbedding> selectByPointIds(Collection<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByPointIds(pointIds);
    }

    @Override
    public int insert(AiDocumentEmbedding entity) {
        return mapper.insert(entity);
    }

    @Override
    public int insertBatch(List<AiDocumentEmbedding> entities) {
        int n = 0;
        for (AiDocumentEmbedding e : entities) {
            n += mapper.insert(e);
        }
        return n;
    }

    @Override
    public int deleteByDocument(Long documentId) {
        return mapper.deleteByDocument(documentId);
    }

    @Override
    public List<AiDocumentEmbedding> selectOrphanEmbeddings(int limit) {
        return mapper.selectOrphanEmbeddings(limit);
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.deleteByIds(ids);
    }
}
