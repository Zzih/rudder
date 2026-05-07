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

package io.github.zzih.rudder.ai.orchestrator.turn;

import io.github.zzih.rudder.ai.context.ContextProfileService;
import io.github.zzih.rudder.ai.orchestrator.ChatClientFactory;
import io.github.zzih.rudder.ai.orchestrator.ContextBuilder;
import io.github.zzih.rudder.ai.orchestrator.TokenFlusher;
import io.github.zzih.rudder.ai.orchestrator.Ulid;
import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;
import io.github.zzih.rudder.ai.orchestrator.message.MessageStatus;
import io.github.zzih.rudder.ai.rag.RudderDocumentRetriever;
import io.github.zzih.rudder.common.metrics.MetricNames;
import io.github.zzih.rudder.dao.entity.AiContextProfile;
import io.github.zzih.rudder.dao.entity.AiSession;
import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.service.config.LlmConfigService;
import io.github.zzih.rudder.service.stream.CancellationHandle;
import io.github.zzih.rudder.service.stream.StreamRegistry;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * CHAT 模式 turn 执行器 —— 走 Spring AI {@link ChatClient} 的 {@code .stream()} 返回
 * {@code Flux<ChatClientResponse>},配合 {@link RedactionAdvisor} + {@link UsageMetricsAdvisor}
 * + {@link SimpleLoggerAdvisor}。
 * <p>
 * Flux 订阅的 {@link Disposable} 绑到 {@link CancellationHandle},
 * {@code streamRegistry.cancel(streamId)} 会直接 dispose subscription,不再依赖 thread interrupt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnExecutor {

    private final LlmConfigService llmConfigService;
    private final MessagePersistence persistence;
    private final StreamRegistry streamRegistry;
    private final MeterRegistry meterRegistry;
    private final ContextBuilder contextBuilder;
    private final ContextProfileService contextProfileService;
    private final ChatClientFactory chatClientFactory;

    public void execute(TurnRequest request, TurnEventSink sink, AiSession session) {
        String turnId = Ulid.newUlid();
        String model = session.getModelSnapshot();

        long startedAt = System.currentTimeMillis();
        var userMsg = persistence.insertUser(request.getSessionId(), turnId, request.getMessage());
        var assistantMsg = persistence.insertAssistantPlaceholder(request.getSessionId(), turnId, model);

        CancellationHandle handle = streamRegistry.register(assistantMsg.getId(), request.getSessionId());
        TokenFlusher flusher = new TokenFlusher(assistantMsg.getId(), persistence);
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();

        try {
            sink.emit(new TurnEvent.Meta(
                    turnId, handle.getStreamId(), request.getSessionId(),
                    userMsg.getId(), assistantMsg.getId()));

            LlmClient client = llmConfigService.required();
            ChatModel chatModel = client.getChatModel();
            if (chatModel == null) {
                throw new IllegalStateException(
                        "active AI provider does not expose Spring AI ChatModel: " + client.getClass().getSimpleName());
            }

            AiContextProfile profile = contextProfileService.resolve(request.getWorkspaceId(), session.getId());
            String systemPrompt = contextBuilder.build(request, session, profile);
            List<Message> history = persistence.loadHistoryExcluding(request.getSessionId(), userMsg.getId());

            boolean ragEnabled = Boolean.TRUE.equals(profile.getInjectWikiRag());

            ChatClient chatClient = chatClientFactory.build(chatModel, request.getWorkspaceId(),
                    promptTokens, completionTokens, ragEnabled);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> lastFinishReason = new AtomicReference<>();
            Disposable disposable = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(history)
                    .user(request.getMessage())
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
                            flusher.append(delta);
                            sink.emit(new TurnEvent.Token(delta));
                        }
                    })
                    .doOnError(error::set)
                    .doFinally(sig -> latch.countDown())
                    .subscribe();
            handle.bindDisposable(disposable);

            latch.await();

            if (error.get() != null) {
                throw new ProviderException(error.get());
            }
            if (handle.isCancelled()) {
                throw new StreamCancelledException();
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
            recordTurnMetrics("CHAT", "DONE", request.getWorkspaceId(), latency);
        } catch (StreamCancelledException ignored) {
            flusher.flush();
            int latency = (int) (System.currentTimeMillis() - startedAt);
            Integer pTok = promptTokens.get() > 0 ? promptTokens.get() : null;
            Integer cTok = completionTokens.get() > 0 ? completionTokens.get() : null;
            persistence.finishMessage(request.getSessionId(), assistantMsg.getId(),
                    MessageStatus.CANCELLED, flusher.currentContent(), null, model,
                    pTok, cTok, null, latency);
            sink.emit(new TurnEvent.Cancelled());
            recordTurnMetrics("CHAT", "CANCELLED", request.getWorkspaceId(), latency);
            meterRegistry.counter(MetricNames.AI_CANCEL_TOTAL, "reason", "user").increment();
            log.info("turn {} cancelled for session {}", turnId, request.getSessionId());
        } catch (Exception e) {
            flusher.flush();
            Throwable cause = e instanceof ProviderException pe ? pe.getCause() : e;
            String err = cause == null || cause.getMessage() == null ? e.getMessage() : cause.getMessage();
            int latency = (int) (System.currentTimeMillis() - startedAt);
            Integer pTok = promptTokens.get() > 0 ? promptTokens.get() : null;
            Integer cTok = completionTokens.get() > 0 ? completionTokens.get() : null;
            persistence.finishMessage(request.getSessionId(), assistantMsg.getId(),
                    MessageStatus.FAILED, flusher.currentContent(), err, model,
                    pTok, cTok, null, latency);
            sink.emit(new TurnEvent.Error(err));
            recordTurnMetrics("CHAT", "FAILED", request.getWorkspaceId(), latency);
            meterRegistry.counter(MetricNames.AI_PROVIDER_ERROR_TOTAL,
                    "provider", model == null ? "unknown" : model,
                    "reason", e.getClass().getSimpleName()).increment();
            log.error("turn {} failed for session {}", turnId, request.getSessionId(), e);
        } finally {
            streamRegistry.unregister(handle.getStreamId());
        }
    }

    private void recordTurnMetrics(String mode, String status, long workspaceId, int latencyMs) {
        meterRegistry.counter(MetricNames.AI_TURN_TOTAL,
                "mode", mode, "status", status, "workspace", String.valueOf(workspaceId)).increment();
        Timer.builder(MetricNames.AI_TURN_DURATION_SECONDS)
                .tag("mode", mode)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(latencyMs));
    }

    private static final class StreamCancelledException extends RuntimeException {
    }

    private static final class ProviderException extends RuntimeException {

        ProviderException(Throwable cause) {
            super(cause);
        }
    }
}
