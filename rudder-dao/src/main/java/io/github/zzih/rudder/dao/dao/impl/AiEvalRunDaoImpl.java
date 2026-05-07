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

import io.github.zzih.rudder.dao.dao.AiEvalRunDao;
import io.github.zzih.rudder.dao.entity.AiEvalRun;
import io.github.zzih.rudder.dao.mapper.AiEvalRunMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiEvalRunDaoImpl implements AiEvalRunDao {

    private final AiEvalRunMapper mapper;

    @Override
    public List<AiEvalRun> selectByBatch(String batchId) {
        return mapper.selectByBatch(batchId);
    }

    @Override
    public List<AiEvalRun> selectByCase(Long caseId, int limit) {
        return mapper.selectPageByCase(new Page<>(1, limit), caseId).getRecords();
    }

    @Override
    public IPage<AiEvalRun> selectPageByCase(Long caseId, int pageNum, int pageSize) {
        return mapper.selectPageByCase(new Page<>(pageNum, pageSize), caseId);
    }

    @Override
    public int insert(AiEvalRun entity) {
        return mapper.insert(entity);
    }
}
