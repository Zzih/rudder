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

import io.github.zzih.rudder.dao.dao.AiDialectDao;
import io.github.zzih.rudder.dao.entity.AiDialect;
import io.github.zzih.rudder.dao.mapper.AiDialectMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiDialectDaoImpl implements AiDialectDao {

    private final AiDialectMapper mapper;

    @Override
    public AiDialect selectByTaskType(String taskType) {
        return mapper.selectByTaskType(taskType);
    }

    @Override
    public List<AiDialect> selectAll() {
        return mapper.selectAllOrdered();
    }

    @Override
    public int insert(AiDialect entity) {
        return mapper.insert(entity);
    }

    @Override
    public int updateById(AiDialect entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteByTaskType(String taskType) {
        return mapper.deleteByTaskType(taskType);
    }
}
