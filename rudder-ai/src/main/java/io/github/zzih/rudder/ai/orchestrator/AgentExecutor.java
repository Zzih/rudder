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

import io.github.zzih.rudder.ai.context.ContextProfileService;
import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;
import io.github.zzih.rudder.ai.orchestrator.message.MessageStatus;
import io.github.zzih.rudder.ai.orchestrator.tool.RudderToolCallback;
import io.github.zzih.rudder.ai.orchestrator.tool.ToolApprovalRegistry;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEvent;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEventSink;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRequest;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnTail;
import io.github.zzih.rudder.ai.rag.RudderDocumentRetriever;
import io.github.zzih.rudder.ai.skill.SkillInvocationContext;
import io.github.zzih.rudder.ai.skill.SkillToolProvider;
import io.github.zzih.rudder.ai.tool.PermissionGate;
import io.github.zzih.rudder.ai.tool.ToolRegistry;
import io.github.zzih.rudder.common.exception.ExceptionFormatter;
import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.dao.entity.AiContextProfile;
import io.github.zzih.rudder.dao.entity.AiSession;
import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.config.LlmConfigService;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.service.stream.CancellationHandle;
import io.github.zzih.rudder.service.stream.StreamRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * AGENT 模式 turn 执行器 —— 流式 ChatClient.stream() + Spring AI 内置 tool execution loop
 * ({@code internalToolExecutionEnabled=true})。每个 chunk 的 text 实时 emit,
 * 中间 tool 调用穿插其中;{@link RudderToolCallback} 负责工具事件 / 权限 / 审批 / 持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    /**
     * 单个 turn 的硬超时。和 {@code AiTurnController} 的 SseEmitter 30min 对齐 —— provider hang 时不永久阻塞
     * HTTP 虚拟线程,让 SSE 客户端能从 Error 收到明确原因。
     */
    private static final long TURN_HARD_TIMEOUT_MINUTES = 30;

    private final LlmConfigService llmConfigService;
    private final MessagePersistence persistence;
    private final AiMessageDao messageDao;
    private final StreamRegistry streamRegistry;
    private final ToolRegistry toolRegistry;
    private final PermissionGate permissionGate;
    private final ContextBuilder contextBuilder;
    private final ToolApprovalRegistry approvalRegistry;
    private final ContextProfileService contextProfileService;
    private final ChatClientFactory chatClientFactory;
    private final SkillToolProvider skillToolProvider;
    private final RedactionService redactionService;

    public void execute(TurnRequest request, TurnEventSink sink, AiSession session) {
        String turnId = Ulid.newUlid();
        String model = session.getModelSnapshot();
        long startedAt = System.currentTimeMillis();

        var userMsg = persistence.insertUser(request.getSessionId(), turnId, request.getMessage());
        var assistantMsg = persistence.insertAssistantPlaceholder(request.getSessionId(), turnId, model);

        CancellationHandle handle = streamRegistry.register(assistantMsg.getId(), request.getSessionId());
        TokenFlusher flusher = new TokenFlusher(assistantMsg.getId(), persistence);
        StreamingRedactor scrubber = new StreamingRedactor(redactionService);
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> lastFinishReason = new AtomicReference<>();

        try {
            sink.emit(new TurnEvent.Meta(
                    turnId, handle.getStreamId(), request.getSessionId(),
                    userMsg.getId(), assistantMsg.getId()));

            ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                    .userId(request.getUserId())
                    .userRole(request.getUserRole())
                    .workspaceId(request.getWorkspaceId())
                    .sessionId(request.getSessionId())
                    .turnId(turnId)
                    .cancelled(handle.getCancelled())
                    .readOnly(false)
                    .contextDatasourceId(request.getContextDatasourceId())
                    .contextScriptCode(request.getContextScriptCode())
                    .contextSelection(request.getContextSelection())
                    .contextPinnedTables(request.getContextPinnedTables())
                    .contextTaskType(request.getContextTaskType())
                    .build();

            LlmClient client = llmConfigService.required();
            ChatModel chatModel = client.getChatModel();
            if (chatModel == null) {
                throw new IllegalStateException(
                        "active AI provider does not expose Spring AI ChatModel: " + client.getClass().getSimpleName());
            }

            AiContextProfile profile = contextProfileService.resolve(request.getWorkspaceId(), session.getId());
            String systemPrompt = contextBuilder.build(request, session, profile);
            List<Message> history = persistence.loadHistoryExcluding(request.getSessionId(), userMsg.getId());
            List<ToolCallback> callbacks = buildCallbacks(
                    request, toolCtx, sink, handle, turnId);

            boolean ragEnabled = Boolean.TRUE.equals(profile.getInjectWikiRag());

            ChatClient chatClient = chatClientFactory.build(chatModel, request.getWorkspaceId(),
                    promptTokens, completionTokens, ragEnabled);

            CountDownLatch latch = new CountDownLatch(1);
            Disposable disposable = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(history)
                    .user(request.getMessage())
                    .toolCallbacks(callbacks)
                    .advisors(spec -> {
                        if (ragEnabled) {
                            spec.param(RudderDocumentRetriever.CTX_WORKSPACE_ID, request.getWorkspaceId());
                            List<String> engines = contextBuilder.allowedEngines(request);
                            if (engines != null) {
                                spec.param(RudderDocumentRetriever.CTX_ENGINE_TYPES, engines);
                            }
                        }
                    })
                    .stream()
                    .chatResponse()
                    .doOnNext(resp -> {
                        if (handle.isCancelled()) {
                            return;
                        }
                        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) {
                            return;
                        }
                        String reason = TurnEvent.finishReason(resp);
                        if (reason != null) {
                            lastFinishReason.set(reason);
                        }
                        String delta = resp.getResult().getOutput().getText();
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }
                        if (TurnEvent.isThinkingChunk(resp)) {
                            sink.emit(new TurnEvent.Thinking(delta));
                        } else {
                            // 流式脱敏:PII 往往跨 token,scrubber 缓冲最后 N 字符等下一批 delta 合并判断
                            String safe = scrubber.append(delta);
                            if (!safe.isEmpty()) {
                                flusher.append(safe);
                                sink.emit(new TurnEvent.Token(safe));
                            }
                        }
                    })
                    .doOnError(error::set)
                    .doFinally(sig -> latch.countDown())
                    .subscribe();
            handle.bindDisposable(disposable);

            if (!latch.await(TURN_HARD_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
                throw new ProviderException(new TimeoutException(
                        "agent turn exceeded " + TURN_HARD_TIMEOUT_MINUTES + " minute hard timeout"));
            }

            if (error.get() != null) {
                throw new ProviderException(error.get());
            }
            if (handle.isCancelled()) {
                throw new StreamCancelledException();
            }

            // 流正常结束:把 scrubber 尾部 buffer 吐出并脱敏
            String scrubberTail = scrubber.flush();
            if (!scrubberTail.isEmpty()) {
                flusher.append(scrubberTail);
                sink.emit(new TurnEvent.Token(scrubberTail));
            }

            TurnTail.appendLengthLimitTail(lastFinishReason.get(), turnId, flusher, sink);
            flusher.flush();
            int latency = (int) (System.currentTimeMillis() - startedAt);
            Integer pTok = promptTokens.get() > 0 ? promptTokens.get() : null;
            Integer cTok = completionTokens.get() > 0 ? completionTokens.get() : null;
            persistence.finishMessage(request.getSessionId(), assistantMsg.getId(),
                    MessageStatus.DONE, flusher.currentContent(), null, model,
                    pTok, cTok, null, latency);
            sink.emit(new TurnEvent.Usage(pTok, cTok, null, model, latency));
            sink.emit(new TurnEvent.Done());
        } catch (StreamCancelledException | RudderToolCallback.AgentCancelledException ignored) {
            drainScrubber(scrubber, flusher, sink);
            flusher.flush();
            int latency = (int) (System.currentTimeMillis() - startedAt);
            Integer pTok = promptTokens.get() > 0 ? promptTokens.get() : null;
            Integer cTok = completionTokens.get() > 0 ? completionTokens.get() : null;
            persistence.finishMessage(request.getSessionId(), assistantMsg.getId(),
                    MessageStatus.CANCELLED, flusher.currentContent(), null, model,
                    pTok, cTok, null, latency);
            sink.emit(new TurnEvent.Cancelled());
            log.info("agent turn {} cancelled", turnId);
        } catch (Exception e) {
            drainScrubber(scrubber, flusher, sink);
            flusher.flush();
            String err = ExceptionFormatter.summarize(e);
            int latency = (int) (System.currentTimeMillis() - startedAt);
            Integer pTok = promptTokens.get() > 0 ? promptTokens.get() : null;
            Integer cTok = completionTokens.get() > 0 ? completionTokens.get() : null;
            persistence.finishMessage(request.getSessionId(), assistantMsg.getId(),
                    MessageStatus.FAILED, flusher.currentContent(), err, model,
                    pTok, cTok, null, latency);
            sink.emit(new TurnEvent.Error(err));
            log.error("agent turn {} failed", turnId, e);
        } finally {
            streamRegistry.unregister(handle.getStreamId());
        }
    }

    // ==================== helpers ====================

    private static void drainScrubber(StreamingRedactor scrubber, TokenFlusher flusher, TurnEventSink sink) {
        String tail = scrubber.flush();
        if (!tail.isEmpty()) {
            flusher.append(tail);
            sink.emit(new TurnEvent.Token(tail));
        }
    }

    private List<ToolCallback> buildCallbacks(
                                              TurnRequest request, ToolExecutionContext toolCtx, TurnEventSink sink,
                                              CancellationHandle handle, String turnId) {
        SkillInvocationContext skillCtx = new SkillInvocationContext(
                sink, persistence, messageDao, permissionGate, approvalRegistry, redactionService,
                handle, request.getSessionId(), turnId);
        List<AgentTool> tools = toolRegistry.allForWorkspace(request.getWorkspaceId());
        tools.addAll(skillToolProvider.buildFor(request.getWorkspaceId(), skillCtx));
        List<ToolCallback> out = new ArrayList<>(tools.size());
        for (AgentTool t : tools) {
            out.add(new RudderToolCallback(
                    t, t.name(), toolCtx, sink, persistence, messageDao,
                    permissionGate, approvalRegistry, redactionService, handle,
                    request.getSessionId(), turnId));
        }
        return out;
    }

    private static final class StreamCancelledException extends RuntimeException {
    }

    private static final class ProviderException extends RuntimeException {

        ProviderException(Throwable cause) {
            super(cause);
        }
    }
}
