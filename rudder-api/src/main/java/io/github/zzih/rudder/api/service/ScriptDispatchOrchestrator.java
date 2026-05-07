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

import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.service.script.ScriptDispatchService;
import io.github.zzih.rudder.service.workflow.WorkflowDefinitionService;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 把脚本 dispatch 成 DAG 节点。原属 ScriptController 内联组装,挪出避免 controller 直接持有
 * Script / WorkflowDefinition entity。
 */
@Service
@RequiredArgsConstructor
public class ScriptDispatchOrchestrator {

    private final ScriptDispatchService scriptDispatchService;
    private final WorkflowDefinitionService workflowDefinitionService;

    public void dispatchToWorkflow(Long workspaceId, Long projectCode, Long scriptCode,
                                   Long workflowDefinitionCode) {
        Script script = scriptDispatchService.getScriptForDispatch(scriptCode);
        WorkflowDefinition workflow =
                workflowDefinitionService.getByCode(workspaceId, projectCode, workflowDefinitionCode);

        TaskDefinitionDTO newDto = new TaskDefinitionDTO();
        newDto.setName(script.getName());
        newDto.setTaskType(script.getTaskType());
        newDto.setScript(script);
        newDto.setIsEnabled(true);

        List<TaskDefinitionDTO> allDefs =
                new ArrayList<>(workflowDefinitionService.listTaskDefinitionDTOs(workflow.getCode()));
        allDefs.add(newDto);

        DagGraph graph;
        if (workflow.getDagJson() != null && !workflow.getDagJson().isBlank()) {
            graph = DagParser.parse(workflow.getDagJson());
        } else {
            graph = new DagGraph();
        }
        DagNode newNode = new DagNode();
        newNode.setLabel(script.getName());
        graph.getNodes().add(newNode);

        WorkflowDefinition update = new WorkflowDefinition();
        update.setDagJson(DagParser.serialize(graph));
        workflowDefinitionService.update(workspaceId, projectCode, workflow.getCode(), update, allDefs);
    }
}
