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

import java.util.List;

import lombok.Builder;
import lombok.Data;

/** 发起一轮 AI 对话的请求参数。 */
@Data
@Builder
public class TurnRequest {

    private long sessionId;
    private long userId;
    /** 当前用户角色(RoleType.name()),Controller 入口就必须填。下游 PermissionGate 会据此拒绝 VIEWER 调写操作。 */
    private String userRole;
    private long workspaceId;
    private String message;

    // ==================== 上下文注入字段 ====================

    /** 当前数据源(SQL 类型决定 dialect)。 */
    private Long contextDatasourceId;

    /** 当前打开脚本(用于 resolve dialect、inject script 摘要)。 */
    private Long contextScriptCode;

    /** 用户选中的文本片段(优先级最高的提示)。 */
    private String contextSelection;

    /** pinned 表引用,格式 "db.table",空串表示没 pin。注入 schema 摘要。 */
    @Builder.Default
    private List<String> contextPinnedTables = List.of();

    /** 覆盖 dialect(若前端已经算好,免得后端再查 script)。比如 "STARROCKS_SQL"。 */
    private String contextTaskType;
}
