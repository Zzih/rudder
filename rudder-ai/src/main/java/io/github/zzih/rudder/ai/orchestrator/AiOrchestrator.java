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

package io.github.zzih.rudder.ai.orchestrator;

import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEvent;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEventSink;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnExecutor;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRequest;
import io.github.zzih.rudder.common.enums.ai.SessionMode;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.dao.entity.AiSession;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Turn 总调度。按 session.mode 分派到 CHAT / AGENT 执行器。
 */
@Component
@RequiredArgsConstructor
public class AiOrchestrator {

    private final TurnExecutor chatExecutor;
    private final AgentExecutor agentExecutor;
    private final MessagePersistence persistence;

    public void executeTurn(TurnRequest request, TurnEventSink sink) {
        AiSession session = persistence.getSession(request.getSessionId());
        if (session == null) {
            sink.emit(new TurnEvent.Error(I18n.t(
                    "err.AiErrorCode.SESSION_NOT_FOUND")));
            return;
        }
        if (SessionMode.from(session.getMode()) == SessionMode.AGENT) {
            agentExecutor.execute(request, sink, session);
        } else {
            chatExecutor.execute(request, sink, session);
        }
    }
}
