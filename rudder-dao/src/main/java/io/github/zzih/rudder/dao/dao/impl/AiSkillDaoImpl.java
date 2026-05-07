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

import io.github.zzih.rudder.dao.dao.AiSkillDao;
import io.github.zzih.rudder.dao.entity.AiSkill;
import io.github.zzih.rudder.dao.mapper.AiSkillMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiSkillDaoImpl implements AiSkillDao {

    private final AiSkillMapper mapper;

    @Override
    public AiSkill selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public AiSkill selectByName(String name) {
        return mapper.queryByName(name);
    }

    @Override
    public List<AiSkill> selectAll() {
        return mapper.queryAll();
    }

    @Override
    public IPage<AiSkill> selectPage(int pageNum, int pageSize) {
        return mapper.selectPageOrdered(new Page<>(pageNum, pageSize));
    }

    @Override
    public int insert(AiSkill entity) {
        return mapper.insert(entity);
    }

    @Override
    public int updateById(AiSkill entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
