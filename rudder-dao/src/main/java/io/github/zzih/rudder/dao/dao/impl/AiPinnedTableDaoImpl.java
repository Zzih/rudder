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

import io.github.zzih.rudder.dao.dao.AiPinnedTableDao;
import io.github.zzih.rudder.dao.entity.AiPinnedTable;
import io.github.zzih.rudder.dao.mapper.AiPinnedTableMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiPinnedTableDaoImpl implements AiPinnedTableDao {

    private final AiPinnedTableMapper mapper;

    @Override
    public List<AiPinnedTable> selectByScope(String scope, Long scopeId) {
        return mapper.selectByScope(scope, scopeId);
    }

    @Override
    public IPage<AiPinnedTable> selectPageByScope(String scope, Long scopeId, int pageNum, int pageSize) {
        return mapper.selectPageByScope(new Page<>(pageNum, pageSize), scope, scopeId);
    }

    @Override
    public AiPinnedTable selectOne(String scope, Long scopeId, Long datasourceId, String database, String table) {
        return mapper.selectByRef(scope, scopeId, datasourceId, database, table);
    }

    @Override
    public int insert(AiPinnedTable entity) {
        return mapper.insert(entity);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    @Override
    public int deleteByScopeAndRef(String scope, Long scopeId, Long datasourceId, String database, String table) {
        return mapper.deleteByRef(scope, scopeId, datasourceId, database, table);
    }
}
