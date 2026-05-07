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

package io.github.zzih.rudder.mcp.tool;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.datasource.service.DatasourceService;

import java.util.Objects;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 工作空间边界守卫 — Tool 调用 service 前先做 workspace 隔离校验。
 *
 * <p>MCP token 强绑单 workspace（{@code mcp_token.workspace_id}），PatAuthFilter
 * 把该 workspace 注入 {@code UserContext}。本守卫验证 tool 涉及的资源（datasource /
 * project / workflow）是否归属当前 workspace，不属于则抛 {@code ForbiddenException}。
 *
 * <p>之所以集中在此而不散落在各 tool：
 * <ul>
 *   <li>避免每个 tool 重复写校验逻辑</li>
 *   <li>service 层的 *WithWorkspace 方法散在各模块，签名不统一，guard 屏蔽差异</li>
 *   <li>越权检查是安全边界，集中后单测易覆盖</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorkspaceGuard {

    /**
     * URI 模板路径段不能为空字符串，所以对单 catalog 引擎（MySQL/Hive/PG…）我们约定 caller 传 {@code "-"} 占位；
     * 此方法把 {@code "-"} 翻译成 service 接受的 {@code null}。
     */
    public static String unwrapCatalog(String catalog) {
        return "-".equals(catalog) ? null : catalog;
    }

    private final DatasourceService datasourceService;
    private final ProjectDao projectDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ScriptDao scriptDao;

    /**
     * 校验数据源在当前 workspace 可见。返回该数据源 id。
     * 不可见 → 抛 ForbiddenException。
     */
    public Long requireDatasourceVisible(String datasourceName) {
        Long workspaceId = UserContext.requireWorkspaceId();
        Long dsId = datasourceService.getIdByName(datasourceName);
        datasourceService.getByIdWithWorkspace(workspaceId, dsId);
        return dsId;
    }

    /** 按 id 校验数据源归属当前 workspace（manage tools 用）。 */
    public void requireDatasourceVisibleById(Long datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("datasourceId required");
        }
        Long workspaceId = UserContext.requireWorkspaceId();
        datasourceService.getByIdWithWorkspace(workspaceId, datasourceId);
    }

    /** 校验 project 归属当前 workspace。 */
    public void requireProjectInWorkspace(Long projectCode) {
        if (projectCode == null) {
            throw new IllegalArgumentException("projectCode required");
        }
        Long workspaceId = UserContext.requireWorkspaceId();
        Project project = projectDao.selectByCode(projectCode);
        if (project == null || !Objects.equals(project.getWorkspaceId(), workspaceId)) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Project not accessible from current workspace: " + projectCode);
        }
    }

    /** 校验 script 归属当前 workspace（execution.run_script 用）。 */
    public void requireScriptInWorkspace(Long scriptCode) {
        if (scriptCode == null) {
            throw new IllegalArgumentException("scriptCode required");
        }
        Long workspaceId = UserContext.requireWorkspaceId();
        Script script = scriptDao.selectByCode(scriptCode);
        if (script == null || !Objects.equals(script.getWorkspaceId(), workspaceId)) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Script not accessible from current workspace: " + scriptCode);
        }
    }

    /** 校验 workflow 归属当前 workspace 且属于指定 project。 */
    public void requireWorkflowInWorkspace(Long projectCode, Long workflowCode) {
        requireProjectInWorkspace(projectCode);
        if (workflowCode == null) {
            throw new IllegalArgumentException("workflowCode required");
        }
        WorkflowDefinition wf = workflowDefinitionDao.selectByCode(workflowCode);
        Long workspaceId = UserContext.requireWorkspaceId();
        if (wf == null
                || !Objects.equals(wf.getWorkspaceId(), workspaceId)
                || !Objects.equals(wf.getProjectCode(), projectCode)) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Workflow not accessible from current workspace: " + workflowCode);
        }
    }
}
