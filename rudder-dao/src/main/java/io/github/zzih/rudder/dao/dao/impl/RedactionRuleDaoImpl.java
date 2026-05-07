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

import io.github.zzih.rudder.dao.dao.RedactionRuleDao;
import io.github.zzih.rudder.dao.entity.RedactionRuleEntity;
import io.github.zzih.rudder.dao.mapper.RedactionRuleMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedactionRuleDaoImpl implements RedactionRuleDao {

    private final RedactionRuleMapper mapper;

    @Override
    public List<RedactionRuleEntity> selectAllEnabled() {
        return mapper.queryAllEnabled();
    }

    @Override
    public List<RedactionRuleEntity> selectAll() {
        return mapper.selectList(null);
    }

    @Override
    public RedactionRuleEntity selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(RedactionRuleEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int updateById(RedactionRuleEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    @Override
    public long countByStrategyCode(String strategyCode) {
        return mapper.countByStrategyCode(strategyCode);
    }
}
