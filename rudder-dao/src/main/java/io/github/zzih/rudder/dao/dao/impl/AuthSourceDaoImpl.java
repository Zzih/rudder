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

import io.github.zzih.rudder.dao.dao.AuthSourceDao;
import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.dao.mapper.AuthSourceMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuthSourceDaoImpl implements AuthSourceDao {

    private final AuthSourceMapper authSourceMapper;

    @Override
    public AuthSource selectById(Long id) {
        return authSourceMapper.selectById(id);
    }

    @Override
    public AuthSource selectByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return authSourceMapper.queryByName(name);
    }

    @Override
    public List<AuthSource> selectAll() {
        return authSourceMapper.queryAll();
    }

    @Override
    public List<AuthSource> selectEnabled() {
        return authSourceMapper.queryEnabled();
    }

    @Override
    public List<AuthSource> selectEnabledByType(AuthSourceType type) {
        if (type == null) {
            return List.of();
        }
        return authSourceMapper.queryEnabledByType(type.name());
    }

    @Override
    public long countEnabledByType(AuthSourceType type) {
        if (type == null) {
            return 0L;
        }
        return authSourceMapper.countEnabledByType(type.name());
    }

    @Override
    public int insert(AuthSource source) {
        return authSourceMapper.insert(source);
    }

    @Override
    public int updateById(AuthSource source) {
        return authSourceMapper.updateById(source);
    }

    @Override
    public int deleteById(Long id) {
        return authSourceMapper.deleteById(id);
    }
}
