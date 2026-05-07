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

package io.github.zzih.rudder.ai.eval;

import io.github.zzih.rudder.ai.orchestrator.ChatClientFactory;
import io.github.zzih.rudder.ai.orchestrator.ContextBuilder;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRequest;
import io.github.zzih.rudder.ai.rag.RudderDocumentRetriever;
import io.github.zzih.rudder.ai.tool.ToolRegistry;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.AiEvalCase;
import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.config.LlmConfigService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 评测执行器。独立于 {@code AgentExecutor},**不触碰 session / message / stream 持久化**,
 * 但复用生产真实的:
 * <ul>
 *   <li>{@link ChatClientFactory}(advisor 链:SimpleLogger / RudderRag / Redaction / UsageMetrics)</li>
 *   <li>{@link ContextBuilder}(system prompt:dialect / schema / skill / script / pinnedTables)</li>
 *   <li>{@link LlmPluginManager}(active provider / model)</li>
 *   <li>{@link ToolRegistry}(AGENT 模式的工具列表)</li>
 * </ul>
 *
 * <p>保证 eval 结果能代表真实 agent 行为的同时,不污染消息表 / stream 注册表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalExecutor {

    private final LlmConfigService llmConfigService;
    private final ChatClientFactory chatClientFactory;
    private final ContextBuilder contextBuilder;
    private final ToolRegistry toolRegistry;

    public OneshotResult run(AiEvalCase evalCase) {
        long start = System.currentTimeMillis();
        OneshotResult.OneshotResultBuilder result = OneshotResult.builder()
                .toolCalls(new ArrayList<>());

        try {
            LlmClient client = llmConfigService.required();
            ChatModel chatModel = client.getChatModel();
            if (chatModel == null) {
                throw new IllegalStateException(
                        "active AI provider does not expose Spring AI ChatModel: "
                                + client.getClass().getSimpleName());
            }
            TurnRequest turn = buildTurnRequest(evalCase);
            String systemPrompt = contextBuilder.build(turn, null);

            List<OneshotResult.ToolInvocation> trace = new ArrayList<>();
            List<ToolCallback> callbacks = buildCallbacks(evalCase, turn, trace);

            AtomicInteger promptTokens = new AtomicInteger();
            AtomicInteger completionTokens = new AtomicInteger();
            AtomicReference<String> lastFinishReason = new AtomicReference<>();

            // eval 永远启用 RAG: 测试场景就是要走完整链路验证
            ChatClient chatClient = chatClientFactory.build(chatModel, turn.getWorkspaceId(),
                    promptTokens, completionTokens, true);
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(turn.getMessage())
                    .toolCallbacks(callbacks)
                    .advisors(spec -> {
                        // RAG 按真实路径注入(case 没 datasource 时 allowedEngines 返回 null,retriever 会跳过 engine 过滤)
                        spec.param(RudderDocumentRetriever.CTX_WORKSPACE_ID, turn.getWorkspaceId());
                        List<String> engines = contextBuilder.allowedEngines(turn);
                        if (engines != null) {
                            spec.param(RudderDocumentRetriever.CTX_ENGINE_TYPES, engines);
                        }
                    })
                    .call()
                    .chatResponse();

            String finalText = extractText(response);
            String model = response != null && response.getMetadata() != null
                    ? response.getMetadata().getModel()
                    : null;

            result.finalText(finalText)
                    .toolCalls(trace)
                    .model(model)
                    .promptTokens(promptTokens.get() > 0 ? promptTokens.get() : null)
                    .completionTokens(completionTokens.get() > 0 ? completionTokens.get() : null)
                    .retrievedContext(extractRetrievedContext(response));
        } catch (Exception e) {
            log.warn("eval case {} execution failed: {}", evalCase.getId(), e.getMessage());
            result.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            result.latencyMs((int) (System.currentTimeMillis() - start));
        }
        return result.build();
    }

    // ==================== helpers ====================

    private TurnRequest buildTurnRequest(AiEvalCase evalCase) {
        TurnRequest.TurnRequestBuilder b = TurnRequest.builder()
                .sessionId(0L) // eval 无 session
                .userId(0L) // eval 无用户
                .workspaceId(evalCase.getWorkspaceId() == null ? 0L : evalCase.getWorkspaceId())
                .message(evalCase.getPrompt())
                .contextDatasourceId(evalCase.getDatasourceId())
                .contextTaskType(evalCase.getTaskType());

        // 解析 contextJson:支持 selection / pinnedTables / scriptCode
        JsonNode ctx = parseJson(evalCase.getContextJson());
        if (ctx != null) {
            String selection = ctx.path("selection").asText(null);
            if (selection != null && !selection.isBlank()) {
                b.contextSelection(selection);
            }
            JsonNode pinned = ctx.path("pinnedTables");
            if (pinned.isArray()) {
                List<String> tables = new ArrayList<>();
                for (JsonNode t : pinned) {
                    tables.add(t.asText());
                }
                b.contextPinnedTables(tables);
            }
            if (ctx.hasNonNull("scriptCode")) {
                b.contextScriptCode(ctx.get("scriptCode").asLong());
            }
        }
        if (b.build().getContextPinnedTables() == null) {
            b.contextPinnedTables(Collections.emptyList());
        }
        return b.build();
    }

    private List<ToolCallback> buildCallbacks(AiEvalCase evalCase, TurnRequest turn,
                                              List<OneshotResult.ToolInvocation> trace) {
        EvalMode mode = EvalMode.parse(evalCase.getMode());
        if (mode != EvalMode.AGENT) {
            return List.of();
        }
        ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                .userId(turn.getUserId())
                .userRole("SUPER_ADMIN") // eval 入口已经是 SUPER_ADMIN 管控,子工具链也按最高权限走
                .workspaceId(turn.getWorkspaceId())
                .sessionId(turn.getSessionId())
                .turnId("eval-" + evalCase.getId())
                .readOnly(false)
                .contextDatasourceId(turn.getContextDatasourceId())
                .contextScriptCode(turn.getContextScriptCode())
                .contextSelection(turn.getContextSelection())
                .contextPinnedTables(turn.getContextPinnedTables())
                .contextTaskType(turn.getContextTaskType())
                .build();
        List<AgentTool> tools = toolRegistry.allForWorkspace(turn.getWorkspaceId());
        List<ToolCallback> callbacks = new ArrayList<>(tools.size());
        for (AgentTool t : tools) {
            callbacks.add(new EvalToolCallback(t, toolCtx, trace));
        }
        return callbacks;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    /**
     * 从 {@code RetrievalAugmentationAdvisor} 写入的 metadata 提取本次 turn 的 retrieved context。
     * 用于喂给 {@code FactCheckingEvaluator} 做幻觉检测。RAG 没启用 / 没检索到时返回空 list。
     */
    @SuppressWarnings("unchecked")
    private List<Document> extractRetrievedContext(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return List.of();
        }
        Object ctx = response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (ctx instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Document) {
            return (List<Document>) list;
        }
        return List.of();
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parseTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
