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

import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.dao.entity.AiMessage;
import io.github.zzih.rudder.dao.mapper.AiMessageMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AiMessageDaoImpl implements AiMessageDao {

    private final AiMessageMapper mapper;

    @Override
    public AiMessage selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<AiMessage> selectBySessionId(Long sessionId) {
        return mapper.queryBySession(sessionId);
    }

    @Override
    public List<AiMessage> selectRecentBySessionId(Long sessionId, int limit) {
        return mapper.queryRecentBySession(sessionId, limit);
    }

    @Override
    public int insert(AiMessage message) {
        return mapper.insert(message);
    }

    @Override
    public int deleteBySessionId(Long sessionId) {
        return mapper.deleteBySessionId(sessionId);
    }

    @Override
    public int updateContent(Long id, String content) {
        return mapper.updateContent(id, content);
    }

    @Override
    public int updateStatus(Long id, String status) {
        return mapper.updateStatus(id, status);
    }

    @Override
    public int updateFinalState(Long id, String status, String content, String errorMessage,
                                String model, Integer promptTokens, Integer completionTokens,
                                Integer costCents, Integer latencyMs) {
        return mapper.updateFinalState(id, status, content, errorMessage, model,
                promptTokens, completionTokens, costCents, latencyMs);
    }
}
