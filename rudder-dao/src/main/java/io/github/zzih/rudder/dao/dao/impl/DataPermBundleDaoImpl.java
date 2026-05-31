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

import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.dao.mapper.DataPermBundleMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermBundleDaoImpl implements DataPermBundleDao {

    private final DataPermBundleMapper mapper;

    @Override
    public DataPermBundle selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DataPermBundle> selectByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }

    @Override
    public DataPermBundle selectByName(String name) {
        return mapper.queryOneByName(name);
    }

    @Override
    public List<DataPermBundle> selectAll() {
        return mapper.selectList(null);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<DataPermBundle> selectPage(
                                                                                   String keyword, int pageNum,
                                                                                   int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DataPermBundle> page =
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
    public int insert(DataPermBundle role) {
        return mapper.insert(role);
    }

    @Override
    public int updateById(DataPermBundle role) {
        return mapper.updateById(role);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
