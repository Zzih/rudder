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

import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SSE 事件封装。AiOrchestrator 通过 {@link TurnEventSink} 发出,AiTurnController 转成 SSE 帧。
 * <p>
 * 封闭 sealed 层级避免漏掉新事件。
 */
public sealed interface TurnEvent {

    /** 首个事件,前端据此建立 turn/message/stream 对应关系。 */
    record Meta(String turnId, String streamId, long sessionId,
            Long userMessageId, Long assistantMessageId) implements TurnEvent {
    }

    /** 流式 token,追加到 assistant 消息 content。 */
    record Token(String text) implements TurnEvent {
    }

    /**
     * 模型推理过程(reasoning/thinking chunk),独立于正文。
     * 前端默认折叠显示"Thought for Xs",点开可查看原文。不落进 message.content。
     */
    record Thinking(String text) implements TurnEvent {
    }

    /**
     * Agent 调用工具(Agent 模式才有)。
     * requiresConfirm=true 时前端必须弹 Apply/Reject,后端暂停执行直到用户决定。
     */
    record ToolCall(String toolCallId, String name, JsonNode input, ToolSource source,
            Long messageId, boolean requiresConfirm) implements TurnEvent {
    }

    /** Agent 工具返回结果。 */
    record ToolResult(String toolCallId, String output, boolean success,
            String errorMessage, Long messageId) implements TurnEvent {
    }

    /** 计费统计(provider 报告),通常在 Done 前发。 */
    record Usage(Integer promptTokens, Integer completionTokens,
            Integer costCents, String model, Integer latencyMs) implements TurnEvent {
    }

    /** 正常完成。 */
    record Done() implements TurnEvent {
    }

    /** 用户取消。 */
    record Cancelled() implements TurnEvent {
    }

    /** 出错(provider / 工具 / 超时)。 */
    record Error(String message) implements TurnEvent {
    }

    /** Tool 来源分类,与 t_r_ai_message.tool_source 对齐。 */
    enum ToolSource {
        NATIVE,
        SKILL,
        MCP
    }

    /** Spring AI 的 Anthropic adapter 在 thinking chunk 上打 {@code properties.thinking=TRUE}。 */
    static boolean isThinkingChunk(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) {
            return false;
        }
        Map<String, Object> props = resp.getResult().getOutput().getMetadata();
        return props != null && Boolean.TRUE.equals(props.get("thinking"));
    }

    /** OpenAI 协议下被截断时是 {@code "length"}。 */
    static String finishReason(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getMetadata() == null) {
            return null;
        }
        return resp.getResult().getMetadata().getFinishReason();
    }
}
