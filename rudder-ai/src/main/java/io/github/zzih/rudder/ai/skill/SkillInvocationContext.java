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

package io.github.zzih.rudder.ai.skill;

import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;
import io.github.zzih.rudder.ai.orchestrator.tool.ToolApprovalRegistry;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnEventSink;
import io.github.zzih.rudder.ai.tool.PermissionGate;
import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.service.stream.CancellationHandle;

/**
 * Skill 子 turn 的运行时上下文。父 turn 把自己这套 {sink / 持久化 / 权限 / 审批 / 取消 handle /
 * sessionId + turnId} 透给 skill,让 skill 内部的写类工具调用**继续走父 turn 的审批弹窗 / 取消 / 落库
 * 流程**(而不是直接无审批执行,绕过安全机制)。
 */
public record SkillInvocationContext(
        TurnEventSink sink,
        MessagePersistence persistence,
        AiMessageDao messageDao,
        PermissionGate permissionGate,
        ToolApprovalRegistry approvalRegistry,
        RedactionService redactionService,
        CancellationHandle handle,
        long sessionId,
        String turnId) {
}
