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

package io.github.zzih.rudder.api.service;

import io.github.zzih.rudder.api.request.ScriptDispatchRequest;
import io.github.zzih.rudder.api.response.ScriptBindingResponse;
import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.enums.execution.DispatchMode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.service.workflow.WorkflowDefinitionService;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptBindingService {

    private final ScriptService scriptService;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ProjectDao projectDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;

    public ScriptBindingResponse getBinding(Long workspaceId, Long scriptCode) {
        Script script = scriptService.getByCode(workspaceId, scriptCode);
        TaskDefinition td = taskDefinitionDao.selectByScriptCode(script.getCode());
        if (td == null) {
            return null;
        }
        WorkflowDefinition wf;
        try {
            wf = workflowDefinitionService.getByCode(td.getWorkflowDefinitionCode());
        } catch (Exception e) {
            return null;
        }
        Project project = projectDao.selectByCode(td.getProjectCode());
        return buildBindingResponse(td, wf, project);
    }

    public ScriptBindingResponse push(Long workspaceId, Long scriptCode,
                                      Long projectCode, ScriptDispatchRequest request) {
        log.info("推送脚本绑定, workspaceId={}, scriptCode={}, projectCode={}, mode={}", workspaceId, scriptCode, projectCode,
                request.getMode());
        Script script = scriptService.getByCode(workspaceId, scriptCode);
        TaskDefinition existingBinding = taskDefinitionDao.selectByScriptCode(script.getCode());

        if (request.getMode() == DispatchMode.NEW) {
            pushNew(script, existingBinding, workspaceId, projectCode, request);
        } else if (request.getMode() == DispatchMode.REPLACE) {
            pushReplace(script, existingBinding, request);
        }

        TaskDefinition td = taskDefinitionDao.selectByScriptCode(script.getCode());
        WorkflowDefinition wf = workflowDefinitionService.getByCode(td.getWorkflowDefinitionCode());
        Project project = projectDao.selectByCode(td.getProjectCode());
        return buildBindingResponse(td, wf, project);
    }

    private void pushNew(Script script, TaskDefinition existingBinding,
                         Long workspaceId, Long projectCode, ScriptDispatchRequest request) {
        if (existingBinding != null) {
            throw new BizException(ScriptErrorCode.SCRIPT_BINDING_EXISTS);
        }
        WorkflowDefinition workflow =
                workflowDefinitionService.getByCode(workspaceId, projectCode, request.getWorkflowDefinitionCode());

        TaskDefinition td = new TaskDefinition();
        td.setCode(CodeGenerateUtils.genCode());
        td.setWorkflowDefinitionCode(workflow.getCode());
        td.setWorkspaceId(workspaceId);
        td.setProjectCode(projectCode);
        td.setName(script.getName());
        td.setTaskType(script.getTaskType());
        td.setScriptCode(script.getCode());
        td.setIsEnabled(true);
        taskDefinitionDao.insert(td);

        DagGraph graph;
        if (workflow.getDagJson() != null && !workflow.getDagJson().isBlank()) {
            graph = DagParser.parse(workflow.getDagJson());
        } else {
            graph = new DagGraph();
        }
        DagNode newNode = new DagNode();
        newNode.setTaskCode(td.getCode());
        newNode.setLabel(script.getName());
        graph.getNodes().add(newNode);

        workflow.setDagJson(DagParser.serialize(graph));
        workflowDefinitionDao.updateById(workflow);
    }

    private void pushReplace(Script script, TaskDefinition existingBinding,
                             ScriptDispatchRequest request) {
        if (existingBinding != null && !existingBinding.getCode().equals(request.getTaskDefinitionCode())) {
            throw new BizException(ScriptErrorCode.SCRIPT_BINDING_FOREIGN);
        }
        TaskDefinition target = taskDefinitionDao.selectByCode(request.getTaskDefinitionCode());
        if (target == null) {
            throw new BizException(ScriptErrorCode.SCRIPT_BINDING_TARGET_NOT_FOUND);
        }
        target.setScriptCode(script.getCode());
        target.setName(script.getName());
        target.setTaskType(script.getTaskType());
        taskDefinitionDao.updateById(target);
    }

    private ScriptBindingResponse buildBindingResponse(TaskDefinition td, WorkflowDefinition wf, Project project) {
        ScriptBindingResponse resp = new ScriptBindingResponse();
        resp.setProjectCode(td.getProjectCode());
        resp.setProjectName(project != null ? project.getName() : null);
        resp.setWorkflowDefinitionCode(td.getWorkflowDefinitionCode());
        resp.setWorkflowName(wf.getName());
        resp.setTaskDefinitionCode(td.getCode());
        resp.setTaskName(td.getName());
        return resp;
    }
}
