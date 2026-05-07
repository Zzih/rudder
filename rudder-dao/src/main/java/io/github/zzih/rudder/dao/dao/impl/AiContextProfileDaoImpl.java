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

import io.github.zzih.rudder.dao.dao.AiContextProfileDao;
import io.github.zzih.rudder.dao.entity.AiContextProfile;
import io.github.zzih.rudder.dao.mapper.AiContextProfileMapper;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiContextProfileDaoImpl implements AiContextProfileDao {

    private final AiContextProfileMapper mapper;

    @Override
    public AiContextProfile selectByScope(String scope, Long scopeId) {
        return mapper.selectByScope(scope, scopeId);
    }

    @Override
    public int upsert(AiContextProfile entity) {
        AiContextProfile existing = mapper.selectByScope(entity.getScope(), entity.getScopeId());
        if (existing == null) {
            return mapper.insert(entity);
        }
        entity.setId(existing.getId());
        return mapper.updateById(entity);
    }

    @Override
    public int deleteByScope(String scope, Long scopeId) {
        return mapper.deleteByScope(scope, scopeId);
    }
}
