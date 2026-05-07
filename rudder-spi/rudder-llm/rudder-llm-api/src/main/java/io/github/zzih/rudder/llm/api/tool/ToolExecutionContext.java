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

package io.github.zzih.rudder.llm.api.tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Builder;
import lombok.Data;

/** 工具执行上下文 —— 带用户 / 工作区 / 取消信号 / 请求上下文。 */
@Data
@Builder
public class ToolExecutionContext {

    private Long userId;
    private String username;
    /** 当前用户角色名(RoleType.name()),PermissionGate 据此比较工具最低角色要求。null 视为 VIEWER。 */
    private String userRole;
    private Long workspaceId;

    /** 会话 id。 */
    private Long sessionId;

    /** Turn ULID,日志/指标都按此串联。 */
    private String turnId;

    /** 由 StreamRegistry 管理,循环里检查并及时退出。 */
    private AtomicBoolean cancelled;

    /** 只读模式(工作区级开关),工具可据此拒绝写操作。 */
    @Builder.Default
    private boolean readOnly = false;

    // ==================== 请求上下文(P1) ====================

    /** 当前数据源(SQL 读/执行的默认目标)。 */
    private Long contextDatasourceId;

    /** 当前打开脚本。update/execute_script 默认作用对象。 */
    private Long contextScriptCode;

    /** 用户在编辑器里选中的文本,优先作为修改依据。 */
    private String contextSelection;

    /** 用户 pin 的表引用,格式 "db.table"。 */
    @Builder.Default
    private List<String> contextPinnedTables = List.of();

    /** 当前 TaskType(决定 dialect / 脚本类型)。 */
    private String contextTaskType;

    public boolean isCancelled() {
        return cancelled != null && cancelled.get();
    }
}
