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

import io.github.zzih.rudder.dao.dao.AiMetadataSyncConfigDao;
import io.github.zzih.rudder.dao.entity.AiMetadataSyncConfig;
import io.github.zzih.rudder.dao.mapper.AiMetadataSyncConfigMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiMetadataSyncConfigDaoImpl implements AiMetadataSyncConfigDao {

    private final AiMetadataSyncConfigMapper mapper;

    @Override
    public AiMetadataSyncConfig selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public AiMetadataSyncConfig selectByDatasourceId(Long datasourceId) {
        if (datasourceId == null) {
            return null;
        }
        return mapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<AiMetadataSyncConfig> selectAll() {
        return mapper.selectAllOrdered();
    }

    @Override
    public IPage<AiMetadataSyncConfig> selectPage(int pageNum, int pageSize) {
        return mapper.selectPageOrdered(new Page<>(pageNum, pageSize));
    }

    @Override
    public List<AiMetadataSyncConfig> selectEnabledWithCron() {
        return mapper.selectEnabledWithCron();
    }

    @Override
    public int insert(AiMetadataSyncConfig entity) {
        return mapper.insert(entity);
    }

    @Override
    public int updateById(AiMetadataSyncConfig entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
