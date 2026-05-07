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

import io.github.zzih.rudder.dao.dao.VersionConfigDao;
import io.github.zzih.rudder.dao.entity.VersionConfig;
import io.github.zzih.rudder.dao.mapper.VersionConfigMapper;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class VersionConfigDaoImpl implements VersionConfigDao {

    private final VersionConfigMapper versionConfigMapper;

    @Override
    public VersionConfig selectActive() {
        return versionConfigMapper.queryActive();
    }

    @Override
    public VersionConfig selectById(Long id) {
        return versionConfigMapper.selectById(id);
    }

    @Override
    public int insert(VersionConfig config) {
        return versionConfigMapper.insert(config);
    }

    @Override
    public int updateById(VersionConfig config) {
        return versionConfigMapper.updateById(config);
    }
}
