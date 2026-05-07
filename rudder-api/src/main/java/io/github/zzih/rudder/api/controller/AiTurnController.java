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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.ai.orchestrator.AiOrchestrator;
import io.github.zzih.rudder.ai.orchestrator.tool.ToolApprovalRegistry;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEvent;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEventSink;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRequest;
import io.github.zzih.rudder.api.request.AiToolApproveRequest;
import io.github.zzih.rudder.api.request.AiTurnRequest;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.stream.StreamRegistry;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 推理触发入口:
 * <ul>
 *   <li>POST /api/ai/sessions/{id}/turns → SSE 流,发起一轮多轮对话</li>
 *   <li>POST /api/ai/streams/{streamId}/cancel → 取消流</li>
 *   <li>POST /api/ai/streams/{streamId}/tool-approve → 工具审批</li>
 * </ul>
 * IDE 按钮(explain/optimize/diagnose)走的也是 /sessions/{id}/turns,前端把模板化的 user message 发过来,
 * 不再有单独的无状态 oneshot 端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@RequireRole(RoleType.DEVELOPER)
public class AiTurnController {

    private final AiOrchestrator orchestrator;
    private final StreamRegistry streamRegistry;
    private final ToolApprovalRegistry toolApprovalRegistry;

    @PostMapping(value = "/sessions/{sessionId}/turns", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuditLog(module = AuditModule.AI, action = AuditAction.CHAT, resourceType = AuditResourceType.AI_SESSION, description = "AI 流式对话", resourceCode = "#sessionId")
    public SseEmitter postTurn(@PathVariable Long sessionId,
                               @RequestBody AiTurnRequest body) {
        // 30 分钟:至少要大于审批等待(5min)+ 多轮 agent 推理 + 工具执行的总上限,
        // 否则用户在 Apply 前等久一点就会被 SseEmitter 提前关掉。
        SseEmitter emitter = new SseEmitter(30L * 60_000L);
        // 虚拟线程跑 orchestrator 时 UserContext ThreadLocal 已脱,必须在这里快照进 request。
        UserContext.UserInfo user = UserContext.get();
        Long workspaceId = UserContext.requireWorkspaceId();
        TurnRequest request = TurnRequest.builder()
                .sessionId(sessionId)
                .userId(user.getUserId())
                .userRole(user.getRole())
                .workspaceId(workspaceId)
                .message(body.getMessage())
                .contextDatasourceId(body.getDatasourceId())
                .contextScriptCode(body.getScriptCode())
                .contextSelection(body.getSelection())
                .contextPinnedTables(body.getPinnedTables() == null ? List.of() : body.getPinnedTables())
                .contextTaskType(body.getTaskType())
                .build();
        TurnEventSink sink = new EmitterSink(emitter);
        Thread.startVirtualThread(() -> {
            try {
                orchestrator.executeTurn(request, sink);
            } catch (Exception e) {
                log.warn("turn executor unhandled: {}", e.getMessage(), e);
                safeEmit(emitter, new TurnEvent.Error(e.getMessage()));
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    // 正常路径:客户端先断开 / emitter 已 complete。debug 足够,不需要打堆栈。
                    log.debug("SSE emitter.complete() swallowed: {}", e.getMessage());
                }
            }
        });
        return emitter;
    }

    @PostMapping("/streams/{streamId}/cancel")
    @AuditLog(module = AuditModule.AI, action = AuditAction.CHAT, resourceType = AuditResourceType.AI_SESSION, description = "取消 AI 流", resourceCode = "#streamId")
    public Result<Boolean> cancel(@PathVariable String streamId) {
        boolean ok = streamRegistry.cancel(streamId);
        return Result.ok(ok);
    }

    @PostMapping("/streams/{streamId}/tool-approve")
    @AuditLog(module = AuditModule.AI, action = AuditAction.CHAT, resourceType = AuditResourceType.AI_SESSION, description = "审批 AI 写类工具", resourceCode = "#streamId")
    public Result<Boolean> approveTool(@PathVariable String streamId, @RequestBody AiToolApproveRequest body) {
        boolean ok = toolApprovalRegistry.complete(streamId, body.getToolCallId(), body.isApproved());
        return Result.ok(ok);
    }

    private static void safeEmit(SseEmitter emitter, TurnEvent event) {
        try {
            emitter.send(eventToFrame(event));
        } catch (Exception e) {
            // 正常路径:客户端已断开。debug 足够,避免 cancel 洪水日志。
            log.debug("SSE safeEmit swallowed: {}", e.getMessage());
        }
    }

    private static SseEmitter.SseEventBuilder eventToFrame(TurnEvent event) {
        String name = switch (event) {
            case TurnEvent.Meta ignored -> "meta";
            case TurnEvent.Token ignored -> "token";
            case TurnEvent.Thinking ignored -> "thinking";
            case TurnEvent.ToolCall ignored -> "tool_call";
            case TurnEvent.ToolResult ignored -> "tool_result";
            case TurnEvent.Usage ignored -> "usage";
            case TurnEvent.Done ignored -> "done";
            case TurnEvent.Cancelled ignored -> "cancelled";
            case TurnEvent.Error ignored -> "error";
        };
        return SseEmitter.event().name(name).data(JsonUtils.toJson(event), MediaType.APPLICATION_JSON);
    }

    /** 从业务线程把事件转成 SSE 帧。 */
    private static final class EmitterSink implements TurnEventSink {

        private final SseEmitter emitter;

        EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void emit(TurnEvent event) {
            try {
                emitter.send(eventToFrame(event));
            } catch (IOException e) {
                log.debug("SSE client disconnected: {}", e.getMessage());
                // 不 rethrow,让 executor 继续跑到结束落库
            }
        }
    }

}
