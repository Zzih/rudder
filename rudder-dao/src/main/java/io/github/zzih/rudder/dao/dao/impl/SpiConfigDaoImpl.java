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

import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.dao.mapper.SpiConfigMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SpiConfigDaoImpl implements SpiConfigDao {

    private final SpiConfigMapper mapper;

    @Override
    public SpiConfig selectActive(SpiType type) {
        return mapper.queryActiveByType(type);
    }

    @Override
    public SpiConfig selectByTypeAndProvider(SpiType type, String provider) {
        return mapper.queryByTypeAndProvider(type, provider);
    }

    @Override
    public List<SpiConfig> selectAllByType(SpiType type) {
        return mapper.queryAllByType(type);
    }

    @Override
    public SpiConfig selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(SpiConfig config) {
        return mapper.insert(config);
    }

    @Override
    public int updateById(SpiConfig config) {
        return mapper.updateById(config);
    }

    @Override
    public int disableOthers(SpiType type, String keepProvider) {
        return mapper.disableOthers(type, keepProvider);
    }
}
