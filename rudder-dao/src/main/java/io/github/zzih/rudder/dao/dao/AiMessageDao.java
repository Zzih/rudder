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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.AiMessage;

import java.util.List;

public interface AiMessageDao {

    AiMessage selectById(Long id);

    List<AiMessage> selectBySessionId(Long sessionId);

    /** 取最近 N 条消息,用于 turn 历史注入,避免长会话全表扫。 */
    List<AiMessage> selectRecentBySessionId(Long sessionId, int limit);

    int insert(AiMessage message);

    /** 流式写入,只更新 content(token flusher 高频调用)。 */
    int updateContent(Long id, String content);

    int updateStatus(Long id, String status);

    /** Session 硬删时级联清理。 */
    int deleteBySessionId(Long sessionId);

    /** 流结束时一次性更新 status/content/usage。 */
    int updateFinalState(Long id,
                         String status,
                         String content,
                         String errorMessage,
                         String model,
                         Integer promptTokens,
                         Integer completionTokens,
                         Integer costCents,
                         Integer latencyMs);
}
