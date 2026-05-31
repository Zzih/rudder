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

import io.github.zzih.rudder.dao.dao.DataPermScopeAccessGroupDao;
import io.github.zzih.rudder.dao.entity.DataPermScopeAccessGroup;
import io.github.zzih.rudder.dao.mapper.DataPermScopeAccessGroupMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermScopeAccessGroupDaoImpl implements DataPermScopeAccessGroupDao {

    private final DataPermScopeAccessGroupMapper mapper;

    @Override
    public List<DataPermScopeAccessGroup> selectAll() {
        return mapper.queryAll();
    }

    @Override
    public List<DataPermScopeAccessGroup> selectByScopeCode(Long scopeCode) {
        return scopeCode == null ? List.of() : mapper.queryByScopeCode(scopeCode);
    }

    @Override
    public List<DataPermScopeAccessGroup> selectByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }

    @Override
    public DataPermScopeAccessGroup selectById(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    @Override
    public int insert(DataPermScopeAccessGroup group) {
        return mapper.insert(group);
    }

    @Override
    public int update(DataPermScopeAccessGroup group) {
        return mapper.updateGroup(group);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    @Override
    public int deleteByScopeCode(Long scopeCode) {
        return mapper.deleteByScopeCode(scopeCode);
    }
}
