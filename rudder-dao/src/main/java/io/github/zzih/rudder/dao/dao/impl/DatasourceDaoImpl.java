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

import io.github.zzih.rudder.dao.dao.DatasourceDao;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.dao.mapper.DatasourceMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DatasourceDaoImpl implements DatasourceDao {

    private final DatasourceMapper datasourceMapper;

    @Override
    public Datasource selectById(Long id) {
        return datasourceMapper.selectById(id);
    }

    @Override
    public Datasource selectOneByName(String name) {
        return datasourceMapper.queryOneByName(name);
    }

    @Override
    public List<Datasource> selectAll() {
        return datasourceMapper.selectList(null);
    }

    @Override
    public List<Datasource> selectByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return datasourceMapper.selectBatchIds(ids);
    }

    @Override
    public long countByName(String name) {
        return datasourceMapper.countByName(name);
    }

    @Override
    public long countByNameExcludeId(String name, Long excludeId) {
        return datasourceMapper.countByNameExcludeId(name, excludeId);
    }

    @Override
    public int insert(Datasource datasource) {
        return datasourceMapper.insert(datasource);
    }

    @Override
    public int updateById(Datasource datasource) {
        return datasourceMapper.updateById(datasource);
    }

    @Override
    public int deleteById(Long id) {
        return datasourceMapper.deleteById(id);
    }
}
