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

import io.github.zzih.rudder.dao.dao.AiSessionDao;
import io.github.zzih.rudder.dao.entity.AiSession;
import io.github.zzih.rudder.dao.mapper.AiSessionMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiSessionDaoImpl implements AiSessionDao {

    private final AiSessionMapper mapper;

    @Override
    public AiSession selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<AiSession> selectByWorkspaceAndUser(Long workspaceId, Long userId) {
        return mapper.queryByWorkspaceAndUser(workspaceId, userId);
    }

    @Override
    public IPage<AiSession> selectPage(Long workspaceId, Long userId, int pageNum, int pageSize) {
        return mapper.selectPageByWorkspaceAndUser(new Page<>(pageNum, pageSize),
                workspaceId == null ? 0L : workspaceId, userId);
    }

    @Override
    public int insert(AiSession session) {
        return mapper.insert(session);
    }

    @Override
    public int updateById(AiSession session) {
        return mapper.updateById(session);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    @Override
    public int incrementUsage(Long id, int promptTokens, int completionTokens, int costCents) {
        return mapper.incrementUsage(id, promptTokens, completionTokens, costCents);
    }
}
