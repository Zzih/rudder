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

import io.github.zzih.rudder.dao.dao.DataPermRoleDao;
import io.github.zzih.rudder.dao.entity.DataPermRole;
import io.github.zzih.rudder.dao.mapper.DataPermRoleMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermRoleDaoImpl implements DataPermRoleDao {

    private final DataPermRoleMapper mapper;

    @Override
    public DataPermRole selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DataPermRole> selectByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }

    @Override
    public DataPermRole selectByName(String name) {
        return mapper.queryOneByName(name);
    }

    @Override
    public List<DataPermRole> selectAll() {
        return mapper.selectList(null);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<DataPermRole> selectPage(
                                                                                 String keyword, int pageNum,
                                                                                 int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DataPermRole> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        return mapper.queryPage(page, keyword);
    }

    @Override
    public long countByName(String name) {
        return mapper.countByName(name);
    }

    @Override
    public long countByNameExcludeId(String name, Long excludeId) {
        return mapper.countByNameExcludeId(name, excludeId);
    }

    @Override
    public int insert(DataPermRole role) {
        return mapper.insert(role);
    }

    @Override
    public int updateById(DataPermRole role) {
        return mapper.updateById(role);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
