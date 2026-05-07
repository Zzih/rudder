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

import io.github.zzih.rudder.dao.dao.AiDocumentDao;
import io.github.zzih.rudder.dao.entity.AiDocument;
import io.github.zzih.rudder.dao.mapper.AiDocumentMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiDocumentDaoImpl implements AiDocumentDao {

    private final AiDocumentMapper mapper;

    @Override
    public AiDocument selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<AiDocument> selectByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectBatchIds(ids);
    }

    @Override
    public List<AiDocument> selectByWorkspace(Long workspaceId, String docType) {
        return mapper.selectByWorkspace(workspaceId, docType);
    }

    @Override
    public IPage<AiDocument> selectPage(Long workspaceId, String docType, int pageNum, int pageSize) {
        return mapper.selectPageByWorkspace(new Page<>(pageNum, pageSize), workspaceId, docType);
    }

    @Override
    public IPage<AiDocument> selectMetaPage(Long workspaceId, String docType, int pageNum, int pageSize) {
        return mapper.selectMetaPageByWorkspace(new Page<>(pageNum, pageSize), workspaceId, docType);
    }

    @Override
    public AiDocument selectBySourceRef(String sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        return mapper.selectBySourceRef(sourceRef);
    }

    @Override
    public List<AiDocument> selectBySourceRefPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return mapper.selectBySourceRefPrefix(prefix);
    }

    @Override
    public List<AiDocument> fulltextSearch(String q, Long workspaceId, String docType,
                                           Collection<String> engineTypes, int limit) {
        return mapper.fulltextSearch(q, workspaceId, docType, engineTypes, limit);
    }

    @Override
    public List<String> listDistinctDocTypes(Long workspaceId) {
        return mapper.selectDistinctDocTypes(workspaceId);
    }

    @Override
    public int insert(AiDocument entity) {
        return mapper.insert(entity);
    }

    @Override
    public int updateById(AiDocument entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
