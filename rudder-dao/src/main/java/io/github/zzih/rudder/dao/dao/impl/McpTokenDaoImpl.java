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

import io.github.zzih.rudder.dao.dao.McpTokenDao;
import io.github.zzih.rudder.dao.entity.McpToken;
import io.github.zzih.rudder.dao.mapper.McpTokenMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class McpTokenDaoImpl implements McpTokenDao {

    private final McpTokenMapper mapper;

    @Override
    public void insert(McpToken token) {
        mapper.insert(token);
    }

    @Override
    public McpToken selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public McpToken selectByIdWithWorkspaceName(Long id) {
        return mapper.queryByIdWithWorkspaceName(id);
    }

    @Override
    public McpToken selectByTokenPrefix(String tokenPrefix) {
        return mapper.queryByTokenPrefix(tokenPrefix);
    }

    @Override
    public List<McpToken> selectByUserId(Long userId) {
        return mapper.queryByUserId(userId);
    }

    @Override
    public List<McpToken> selectByWorkspaceId(Long workspaceId) {
        return mapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public int updateById(McpToken token) {
        return mapper.updateById(token);
    }

    @Override
    public int revokeIfActive(Long id, String revokedReason) {
        return mapper.revokeIfActive(id, LocalDateTime.now(), revokedReason);
    }

    @Override
    public int touchLastUsed(Long id, String lastUsedIp) {
        return mapper.touchLastUsed(id, LocalDateTime.now(), lastUsedIp);
    }

    @Override
    public List<Long> selectExpiredActiveIds(int limit) {
        return mapper.queryExpiredActiveIds(LocalDateTime.now(), limit);
    }

    @Override
    public int markExpiredIfActive(Long id) {
        return mapper.markExpiredIfActive(id);
    }
}
