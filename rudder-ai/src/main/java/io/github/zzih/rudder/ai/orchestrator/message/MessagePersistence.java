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

package io.github.zzih.rudder.ai.orchestrator.message;

import io.github.zzih.rudder.ai.orchestrator.turn.TurnRole;
import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.dao.dao.AiSessionDao;
import io.github.zzih.rudder.dao.entity.AiMessage;
import io.github.zzih.rudder.dao.entity.AiSession;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** AI 所有 DB 写入的单一入口。Orchestrator 只通过这里落库。 */
@Component
@RequiredArgsConstructor
public class MessagePersistence {

    private final AiSessionDao sessionDao;
    private final AiMessageDao messageDao;

    /** 插入 user 消息。status=DONE(用户文本没有流态)。 */
    public AiMessage insertUser(long sessionId, String turnId, String content) {
        AiMessage m = new AiMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setRole(TurnRole.USER);
        m.setStatus(MessageStatus.DONE);
        m.setContent(content);
        messageDao.insert(m);
        return m;
    }

    /** 插入 assistant 占位消息,初始 PENDING。随流式更新 content、最终 finishMessage。 */
    public AiMessage insertAssistantPlaceholder(long sessionId, String turnId, String model) {
        AiMessage m = new AiMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setRole(TurnRole.ASSISTANT);
        m.setStatus(MessageStatus.PENDING);
        m.setContent("");
        m.setModel(model);
        messageDao.insert(m);
        return m;
    }

    /** 首个 token 到达时切换 PENDING → STREAMING。 */
    public void markStreaming(long messageId) {
        messageDao.updateStatus(messageId, MessageStatus.STREAMING);
    }

    /** Token flusher 调用:只刷 content(高频调用)。 */
    public void flushContent(long messageId, String content) {
        messageDao.updateContent(messageId, content);
    }

    /**
     * 流结束(DONE / CANCELLED / FAILED)。一次性写终态 + 计费,并把 token/cost 累加到 session。
     */
    public void finishMessage(long sessionId, long messageId, String status, String content,
                              String errorMessage, String model,
                              Integer promptTokens, Integer completionTokens,
                              Integer costCents, Integer latencyMs) {
        messageDao.updateFinalState(messageId, status, content, errorMessage, model,
                promptTokens, completionTokens, costCents, latencyMs);
        int p = promptTokens == null ? 0 : promptTokens;
        int c = completionTokens == null ? 0 : completionTokens;
        int cost = costCents == null ? 0 : costCents;
        if (p > 0 || c > 0 || cost > 0) {
            sessionDao.incrementUsage(sessionId, p, c, cost);
        }
    }

    public AiSession getSession(long sessionId) {
        return sessionDao.selectById(sessionId);
    }

    /**
     * 读 session 历史,转成 Spring AI {@link Message}。跳过 {@code excludeMsgId}(通常是本轮 user
     * 消息,已由调用方单独 {@code .user(...)} 传入)。工具 call/result 行不回放 —— Spring AI
     * 内部 tool-execution loop 会用当前轮的 tool_response 自然接上。
     */
    /** 历史窗口上限:超过 token 窗口的老消息不送给 LLM。Agent 工具多轮可能生成很多行,开得比 chat 稍大。 */
    private static final int HISTORY_WINDOW = 80;

    public List<Message> loadHistoryExcluding(long sessionId, long excludeMsgId) {
        var rows = messageDao.selectRecentBySessionId(sessionId, HISTORY_WINDOW);
        List<Message> out = new ArrayList<>();
        for (var row : rows) {
            if (row.getId().equals(excludeMsgId)) {
                continue;
            }
            if (TurnRole.USER.equals(row.getRole())) {
                out.add(new UserMessage(row.getContent() == null ? "" : row.getContent()));
            } else if (TurnRole.ASSISTANT.equals(row.getRole())
                    && MessageStatus.DONE.equals(row.getStatus())
                    && row.getContent() != null && !row.getContent().isEmpty()) {
                out.add(new AssistantMessage(row.getContent()));
            }
        }
        return out;
    }
}
