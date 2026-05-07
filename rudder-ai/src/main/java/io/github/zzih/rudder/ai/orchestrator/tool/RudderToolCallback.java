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

package io.github.zzih.rudder.ai.orchestrator.tool;

import io.github.zzih.rudder.ai.orchestrator.Ulid;
import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEvent;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEventSink;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRole;
import io.github.zzih.rudder.ai.tool.PermissionGate;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.mcp.McpToolNames;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.service.stream.CancellationHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link ToolCallback} 适配器,把 Rudder 的 {@link AgentTool} 包装成 Spring AI 工具。
 * <p>
 * ChatClient 的 internal tool execution loop 每轮 tool_use 命中后会调 {@link #call(String)},
 * 我们在这里 hook:
 * <ol>
 *   <li>落 tool_call 行 + emit {@link TurnEvent.ToolCall}</li>
 *   <li>权限 + readOnly 校验</li>
 *   <li>requiresConfirm 阻塞等前端审批</li>
 *   <li>调 AgentTool.execute</li>
 *   <li>落 tool_result 行 + emit {@link TurnEvent.ToolResult}</li>
 * </ol>
 * 抛 {@link AgentCancelledException} 让 Spring AI 停止 loop(被 AgentExecutor 捕获转取消事件);
 * 其他异常转成字符串 result 返给 LLM,允许 LLM 自愈下一轮。
 */
@Slf4j
public class RudderToolCallback implements ToolCallback {

    private final AgentTool tool;
    private final String llmToolName;
    private final ToolExecutionContext toolCtx;
    private final TurnEventSink sink;
    private final MessagePersistence persistence;
    private final AiMessageDao messageDao;
    private final PermissionGate permissionGate;
    private final ToolApprovalRegistry approvalRegistry;
    private final RedactionService redactionService;
    private final CancellationHandle handle;
    private final long sessionId;
    private final String turnId;

    public RudderToolCallback(
                              AgentTool tool,
                              String llmToolName,
                              ToolExecutionContext toolCtx,
                              TurnEventSink sink,
                              MessagePersistence persistence,
                              AiMessageDao messageDao,
                              PermissionGate permissionGate,
                              ToolApprovalRegistry approvalRegistry,
                              RedactionService redactionService,
                              CancellationHandle handle,
                              long sessionId,
                              String turnId) {
        this.tool = tool;
        this.llmToolName = llmToolName;
        this.toolCtx = toolCtx;
        this.sink = sink;
        this.persistence = persistence;
        this.messageDao = messageDao;
        this.permissionGate = permissionGate;
        this.approvalRegistry = approvalRegistry;
        this.redactionService = redactionService;
        this.handle = handle;
        this.sessionId = sessionId;
        this.turnId = turnId;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        JsonNode schema = tool.inputSchema();
        return DefaultToolDefinition.builder()
                .name(llmToolName)
                .description(tool.description() == null ? "" : tool.description())
                .inputSchema(schema == null ? "{}" : schema.toString())
                .build();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    public String call(String toolInput) {
        if (handle.isCancelled()) {
            throw new AgentCancelledException();
        }

        String toolCallId = Ulid.newUlid();
        JsonNode input = parseInputOrEmpty(toolInput);
        // LLM 合成的 tool_input 可能含用户 PII(例如把邮箱拼进 description 字段)——持久化/推流前过一遍脱敏
        JsonNode safeInput = scrubInput(input);
        boolean requiresConfirm = permissionGate.requiresConfirm(llmToolName, toolCtx.getWorkspaceId());

        Long callRowId = insertToolCall(toolCallId, safeInput);
        TurnEvent.ToolSource src = McpToolNames.isMcp(llmToolName)
                ? TurnEvent.ToolSource.MCP
                : TurnEvent.ToolSource.NATIVE;
        sink.emit(new TurnEvent.ToolCall(toolCallId, llmToolName, safeInput, src, callRowId, requiresConfirm));

        String resultText;
        boolean success;
        String errMsg = null;
        long toolStart = System.currentTimeMillis();
        try {
            permissionGate.check(llmToolName, toolCtx);
            if (requiresConfirm) {
                waitForApproval(handle.getStreamId(), toolCallId);
            }
            if (handle.isCancelled()) {
                throw new AgentCancelledException();
            }
            resultText = tool.execute(input, toolCtx);
            success = true;
        } catch (AgentCancelledException cancel) {
            // 取消:仍然落一条 tool_result 标记失败,再抛出打断 Spring AI loop
            resultText = "Cancelled.";
            success = false;
            errMsg = "cancelled";
            int latency = (int) (System.currentTimeMillis() - toolStart);
            String safeResult = scrubText(resultText);
            Long resultRowId = insertToolResult(toolCallId, safeResult, success, latency);
            sink.emit(new TurnEvent.ToolResult(toolCallId, safeResult, success, errMsg, resultRowId));
            throw cancel;
        } catch (ToolRejectedException rejected) {
            resultText = "User rejected the operation.";
            success = false;
            errMsg = "rejected";
        } catch (Exception e) {
            resultText = "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            success = false;
            errMsg = e.getMessage();
            log.warn("tool {} failed: {}", llmToolName, errMsg);
        }
        int latency = (int) (System.currentTimeMillis() - toolStart);
        // 统一脱敏:持久化 / 推流 / 返回 LLM 都用同一份脱敏文本,避免 LLM 原文复述时绕过出站脱敏
        String safeResult = scrubText(resultText);
        Long resultRowId = insertToolResult(toolCallId, safeResult, success, latency);
        sink.emit(new TurnEvent.ToolResult(toolCallId, safeResult, success, errMsg, resultRowId));
        return safeResult;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return call(toolInput);
    }

    // ==================== internal ====================

    private JsonNode parseInputOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return JsonUtils.createObjectNode();
        }
        try {
            return JsonUtils.parseTree(raw);
        } catch (Exception e) {
            return JsonUtils.createObjectNode();
        }
    }

    /** 对 tool_input JSON 做保守脱敏:序列化 → scrub → 反序列化;任何异常都回退原 input。 */
    private JsonNode scrubInput(JsonNode input) {
        if (redactionService == null || input == null) {
            return input;
        }
        try {
            String serialized = input.toString();
            String cleaned = redactionService.scrubText(serialized);
            if (cleaned == null || cleaned.equals(serialized)) {
                return input;
            }
            return JsonUtils.parseTree(cleaned);
        } catch (Exception e) {
            return input;
        }
    }

    private String scrubText(String text) {
        if (redactionService == null || text == null || text.isEmpty()) {
            return text;
        }
        String cleaned = redactionService.scrubText(text);
        return cleaned == null ? text : cleaned;
    }

    private Long insertToolCall(String toolCallId, JsonNode input) {
        var m = new io.github.zzih.rudder.dao.entity.AiMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setRole(TurnRole.TOOL_CALL);
        m.setToolCallId(toolCallId);
        m.setToolName(llmToolName);
        m.setToolSource(tool.source() == null ? "NATIVE" : tool.source().name());
        m.setToolInput(input == null ? null : input.toString());
        messageDao.insert(m);
        return m.getId();
    }

    private Long insertToolResult(String toolCallId, String output, boolean success, int latencyMs) {
        var m = new io.github.zzih.rudder.dao.entity.AiMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setRole(TurnRole.TOOL_RESULT);
        m.setToolCallId(toolCallId);
        m.setToolName(llmToolName);
        m.setToolSource(tool.source() == null ? "NATIVE" : tool.source().name());
        m.setToolOutput(output);
        m.setToolSuccess(success);
        m.setToolLatencyMs(latencyMs);
        messageDao.insert(m);
        return m.getId();
    }

    /**
     * 阻塞等前端审批。
     *
     * <p>机制:
     * <ul>
     *   <li>本地 future 由同节点的 complete 即时唤醒(~μs)</li>
     *   <li>跨节点 complete 走 pub/sub 广播,本节点订阅者收到后 complete 本地 future(~10-50ms)</li>
     *   <li>外层 {@code future.get(1s)} 超时让我们每秒检查一次 {@link CancellationHandle#isCancelled}</li>
     * </ul>
     *
     * <p>退出:
     * <ul>
     *   <li>APPROVED → 返回,外层继续 tool.execute</li>
     *   <li>REJECTED → 抛 {@link ToolRejectedException}</li>
     *   <li>5min deadline 到仍未完成 → 当拒绝处理</li>
     *   <li>turn 中途被 cancel → 抛 {@link AgentCancelledException}</li>
     * </ul>
     */
    private void waitForApproval(String streamId, String toolCallId) {
        CompletableFuture<Boolean> future = approvalRegistry.register(streamId, toolCallId);
        long deadline = System.currentTimeMillis() + ToolApprovalRegistry.TIMEOUT.toMillis();
        try {
            while (System.currentTimeMillis() < deadline) {
                if (handle.isCancelled()) {
                    throw new AgentCancelledException();
                }
                try {
                    Boolean approved = future.get(1, TimeUnit.SECONDS);
                    if (Boolean.TRUE.equals(approved)) {
                        return;
                    }
                    throw new ToolRejectedException();
                } catch (TimeoutException poll) {
                    // 1 秒到了仍未 complete,下一轮循环
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AgentCancelledException();
                } catch (ExecutionException ee) {
                    throw new ToolRejectedException();
                }
            }
            log.warn("tool approval timeout: stream={} call={}", streamId, toolCallId);
            throw new ToolRejectedException();
        } finally {
            approvalRegistry.unregister(streamId, toolCallId);
        }
    }

    /** 用户取消期间从 tool 调用里抛出,AgentExecutor 捕获后落 CANCELLED 状态。 */
    public static final class AgentCancelledException extends RuntimeException {
    }

    /** 审批被 reject / 超时,同个 turn 内向 LLM 回一条 "User rejected" tool_result。 */
    public static final class ToolRejectedException extends RuntimeException {
    }
}
