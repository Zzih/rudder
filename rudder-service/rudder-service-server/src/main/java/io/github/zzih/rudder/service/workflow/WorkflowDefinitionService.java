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

package io.github.zzih.rudder.service.workflow;

import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionConverter;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowDefinitionDTO;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ScriptDao scriptDao;
    private final WorkflowVersionService workflowVersionService;

    @Transactional
    public WorkflowDefinition create(WorkflowDefinition wf) {
        log.info("创建工作流, projectCode={}, name={}", wf.getProjectCode(), wf.getName());
        if (workflowDefinitionDao.countByProjectCodeAndName(wf.getProjectCode(), wf.getName()) > 0) {
            log.warn("创建工作流失败: 名称已存在, projectCode={}, name={}", wf.getProjectCode(), wf.getName());
            throw new BizException(WorkflowErrorCode.WF_NAME_EXISTS);
        }
        if (wf.getCode() == null) {
            wf.setCode(CodeGenerateUtils.genCode());
        }
        workflowDefinitionDao.insert(wf);
        log.info("工作流创建成功, code={}, name={}", wf.getCode(), wf.getName());
        return wf;
    }

    public WorkflowDefinition getByCode(Long code) {
        WorkflowDefinition wf = workflowDefinitionDao.selectByCode(code);
        if (wf == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
        }
        return wf;
    }

    public WorkflowDefinition getByCode(Long workspaceId, Long projectCode, Long code) {
        WorkflowDefinition wf =
                workflowDefinitionDao.selectByWorkspaceIdAndProjectCodeAndCode(workspaceId, projectCode, code);
        if (wf == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
        }
        return wf;
    }

    public List<WorkflowDefinition> listByProjectCode(Long projectCode) {
        return workflowDefinitionDao.selectByProjectCodeOrderByUpdatedAtDesc(projectCode);
    }

    public IPage<WorkflowDefinition> pageByProjectCode(Long projectCode, String searchVal, int pageNum, int pageSize) {
        return workflowDefinitionDao.selectPageByProjectCode(projectCode, searchVal, pageNum, pageSize);
    }

    public List<WorkflowDefinition> listByWorkspaceId(Long workspaceId) {
        return workflowDefinitionDao.selectByWorkspaceIdOrderByUpdatedAtDesc(workspaceId);
    }

    /**
     * 更新工作流定义。
     */
    @Transactional
    public WorkflowDefinition update(Long workspaceId, Long projectCode, Long code, WorkflowDefinition wf,
                                     List<TaskDefinitionDTO> taskDTOs) {
        log.info("更新工作流, workspaceId={}, projectCode={}, code={}", workspaceId, projectCode, code);
        WorkflowDefinition existing = getByCode(workspaceId, projectCode, code);

        if (wf.getName() != null) {
            existing.setName(wf.getName());
        }
        if (wf.getDescription() != null) {
            existing.setDescription(wf.getDescription());
        }
        if (wf.getGlobalParams() != null) {
            existing.setGlobalParams(wf.getGlobalParams());
        }

        // 同步任务定义（直接接收 DTO，内部处理 Script 持久化）
        List<TaskDefinition> taskEntities = null;
        if (taskDTOs != null) {
            taskEntities = syncTaskDefinitions(existing.getCode(), existing.getWorkspaceId(), existing.getProjectCode(),
                    taskDTOs);
        }

        if (wf.getDagJson() != null) {
            if (taskEntities != null) {
                existing.setDagJson(rebuildDagJson(wf.getDagJson(), taskEntities));
            } else {
                existing.setDagJson(wf.getDagJson());
            }

            if (!existing.getDagJson().isBlank()) {
                try {
                    DagGraph graph = DagParser.parse(existing.getDagJson());
                    graph.validate();
                } catch (Exception e) {
                    log.warn("DAG validation warning for workflow {}: {}", code, e.getMessage());
                }
            }

        }

        workflowDefinitionDao.updateById(existing);
        return existing;
    }

    @Transactional
    public WorkflowDefinition rollback(Long workspaceId, Long projectCode, Long code,
                                       String snapshotContent, Integer rollbackToVersionNo) {
        log.info("回滚工作流, workspaceId={}, projectCode={}, code={}, rollbackToVersion={}", workspaceId, projectCode, code,
                rollbackToVersionNo);
        // 悲观锁：防止并发回滚
        WorkflowDefinition existing = workflowDefinitionDao.selectForUpdateByCode(code);
        if (existing == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND, code);
        }

        WorkflowVersionService.WorkflowSnapshot snapshot = parseSnapshot(snapshotContent);

        // 1. 覆盖 DAG + globalParams
        existing.setDagJson(snapshot.getDagJson());
        if (snapshot.getGlobalParams() != null) {
            existing.setGlobalParams(snapshot.getGlobalParams());
        }
        workflowDefinitionDao.updateById(existing);

        // 2. 物理删除当前工作流所有任务定义，从快照重建
        if (snapshot.getTaskDefinitions() != null) {
            taskDefinitionDao.deleteByWorkflowDefinitionCode(existing.getCode());

            for (WorkflowVersionService.TaskSnapshot ts : snapshot.getTaskDefinitions()) {
                // 新建任务定义
                TaskDefinition td = new TaskDefinition();
                td.setCode(ts.getCode());
                td.setWorkflowDefinitionCode(existing.getCode());
                td.setWorkspaceId(workspaceId);
                td.setProjectCode(projectCode);
                if (ts.getTaskType() != null) {
                    td.setTaskType(TaskType.valueOf(ts.getTaskType()));
                }
                applyTaskSnapshot(td, ts);

                // 恢复关联脚本
                if (ts.getScriptCode() != null) {
                    if (ts.getScriptContent() != null) {
                        td.setScriptCode(restoreScript(ts, workspaceId));
                    } else {
                        // 快照有 scriptCode 但无内容，仅在脚本仍存在时保留绑定
                        Script found = scriptDao.selectByCode(ts.getScriptCode());
                        if (found != null) {
                            td.setScriptCode(ts.getScriptCode());
                        }
                    }
                }

                taskDefinitionDao.insert(td);
            }
        }

        // 3. 创建回滚版本记录
        workflowVersionService.saveWorkflowVersion(existing, existing.getDagJson(),
                "Rollback to v" + rollbackToVersionNo);
        return existing;
    }

    /**
     * 恢复脚本：按 code 查找，存在且未被其他工作流占用则更新；否则按名字查找或新建。
     */
    private Long restoreScript(WorkflowVersionService.TaskSnapshot ts, Long workspaceId) {
        Script script = scriptDao.selectByCode(ts.getScriptCode());
        if (script != null) {
            // script_code 是否已被其他工作流的任务占用（当前工作流的任务已被删除）
            TaskDefinition boundTask = taskDefinitionDao.selectByScriptCode(ts.getScriptCode());
            if (boundTask == null) {
                script.setContent(ts.getScriptContent());
                script.setName(ts.getName());
                scriptDao.updateById(script);
                return script.getCode();
            }
            // 被占用 → 需要创建新脚本
        }

        // 按 (workspaceId, name) 查找同名脚本，有则复用更新
        Script byName = scriptDao.selectByWorkspaceIdAndName(workspaceId, ts.getName());
        if (byName != null) {
            byName.setContent(ts.getScriptContent());
            scriptDao.updateById(byName);
            return byName.getCode();
        }

        // 完全不存在 → 新建（名字加雪花ID后缀避免唯一键冲突）
        Long newCode = CodeGenerateUtils.genCode();
        Script newScript = new Script();
        newScript.setCode(newCode);
        newScript.setWorkspaceId(workspaceId);
        newScript.setName(ts.getName() + "_" + newCode);
        if (ts.getTaskType() != null) {
            newScript.setTaskType(TaskType.valueOf(ts.getTaskType()));
        }
        newScript.setContent(ts.getScriptContent());
        newScript.setSourceType(SourceType.TASK);
        scriptDao.insert(newScript);
        return newScript.getCode();
    }

    private void applyTaskSnapshot(TaskDefinition td, WorkflowVersionService.TaskSnapshot ts) {
        td.setName(ts.getName());
        td.setConfigJson(ts.getConfigJson());
        td.setDescription(ts.getDescription());
        td.setInputParams(ts.getInputParams());
        td.setOutputParams(ts.getOutputParams());
        td.setPriority(ts.getPriority());
        td.setDelayTime(ts.getDelayTime());
        td.setTimeout(ts.getTimeout());
        td.setTimeoutEnabled(ts.getTimeoutEnabled());
        td.setRetryTimes(ts.getRetryTimes());
        td.setRetryInterval(ts.getRetryInterval());
        td.setIsEnabled(ts.getIsEnabled());
    }

    private WorkflowVersionService.WorkflowSnapshot parseSnapshot(String content) {
        return JsonUtils.fromJson(content, WorkflowVersionService.WorkflowSnapshot.class);
    }

    @Transactional
    public void delete(Long workspaceId, Long projectCode, Long code) {
        log.info("删除工作流, workspaceId={}, projectCode={}, code={}", workspaceId, projectCode, code);
        WorkflowDefinition existing = getByCode(workspaceId, projectCode, code);
        taskDefinitionDao.deleteByWorkflowDefinitionCode(existing.getCode());
        workflowDefinitionDao.deleteById(existing.getId());
    }

    public List<TaskDefinition> listTaskDefinitions(Long workflowDefinitionCode) {
        return taskDefinitionDao.selectByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    /**
     * 查询任务定义 DTO 列表，非控制流任务携带完整 Script 对象。
     */
    public List<TaskDefinitionDTO> listTaskDefinitionDTOs(Long workflowDefinitionCode) {
        List<TaskDefinition> taskDefs = taskDefinitionDao.selectByWorkflowDefinitionCode(workflowDefinitionCode);

        // 批量查出关联的 Script
        Set<Long> scriptCodes = taskDefs.stream()
                .map(TaskDefinition::getScriptCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Script> scriptMap = scriptCodes.isEmpty()
                ? Map.of()
                : scriptDao.selectByCodes(scriptCodes).stream()
                        .collect(Collectors.toMap(Script::getCode, Function.identity()));

        return taskDefs.stream().map(td -> {
            TaskDefinitionDTO dto = TaskDefinitionConverter.toDTO(td);
            if (td.getScriptCode() != null) {
                dto.setScript(scriptMap.get(td.getScriptCode()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 同步任务定义：按 code 匹配——已存在的更新，不存在的新增，多余的删除。
     * 非控制流任务从 DTO 的 script 字段创建/更新 TASK 来源的 Script 记录。
     *
     * @return 同步后的 TaskDefinition entity 列表（用于 rebuildDagJson）
     */
    private List<TaskDefinition> syncTaskDefinitions(Long workflowDefinitionCode, Long workspaceId, Long projectCode,
                                                     List<TaskDefinitionDTO> taskDTOs) {
        Map<Long, TaskDefinition> existingByCode =
                taskDefinitionDao.selectByWorkflowDefinitionCode(workflowDefinitionCode)
                        .stream()
                        .collect(Collectors.toMap(TaskDefinition::getCode, Function.identity()));

        Set<Long> incomingCodes = new HashSet<>();
        List<TaskDefinition> result = new ArrayList<>();

        for (TaskDefinitionDTO dto : taskDTOs) {
            // 非控制流任务：持久化 Script
            persistScript(dto, workspaceId);

            TaskDefinition td = TaskDefinitionConverter.toEntity(dto);
            td.setWorkflowDefinitionCode(workflowDefinitionCode);
            td.setWorkspaceId(workspaceId);
            td.setProjectCode(projectCode);

            if (td.getCode() != null && existingByCode.containsKey(td.getCode())) {
                TaskDefinition existing = existingByCode.get(td.getCode());
                td.setId(existing.getId());
                taskDefinitionDao.updateById(td);
                incomingCodes.add(td.getCode());
            } else {
                if (td.getCode() == null) {
                    td.setCode(CodeGenerateUtils.genCode());
                }
                taskDefinitionDao.insert(td);
                incomingCodes.add(td.getCode());
            }
            result.add(td);
        }

        // 删除不再存在的
        for (Map.Entry<Long, TaskDefinition> entry : existingByCode.entrySet()) {
            if (!incomingCodes.contains(entry.getKey())) {
                taskDefinitionDao.deleteById(entry.getValue().getId());
            }
        }

        return result;
    }

    /**
     * 从 DTO 的 script 字段创建或更新 TASK 来源的 Script 记录，
     * 并确保 script.code 已设置（供 Converter 提取 scriptCode）。
     * 所有任务类型（包括控制流）都通过 Script 存储配置。
     */
    private void persistScript(TaskDefinitionDTO dto, Long workspaceId) {
        Script script = dto.getScript();
        if (script == null) {
            return;
        }

        script.setWorkspaceId(workspaceId);
        script.setTaskType(dto.getTaskType());
        if (script.getName() == null || script.getName().isBlank()) {
            script.setName(dto.getName());
        }

        // 优先用 script 自身的 code，其次从已有 TaskDefinition 的 scriptCode 获取
        Long scriptCode = script.getCode();
        if (scriptCode == null && dto.getCode() != null) {
            TaskDefinition existingTd = taskDefinitionDao.selectByCode(dto.getCode());
            if (existingTd != null && existingTd.getScriptCode() != null) {
                scriptCode = existingTd.getScriptCode();
                script.setCode(scriptCode);
            }
        }

        if (scriptCode != null) {
            Script existing = scriptDao.selectByCode(scriptCode);
            if (existing != null) {
                existing.setContent(script.getContent());
                existing.setTaskType(dto.getTaskType());
                existing.setName(script.getName());
                scriptDao.updateById(existing);
            }
        } else {
            Long newCode = CodeGenerateUtils.genCode();
            script.setCode(newCode);
            script.setName(dto.getName() + "_" + newCode);
            script.setSourceType(SourceType.TASK);
            scriptDao.insert(script);
        }
        dto.setScript(script);
    }

    /**
     * 用 taskCode 重建 dagJson。
     */
    private String rebuildDagJson(String dagJson, List<TaskDefinition> taskDefinitions) {
        DagGraph graph = DagParser.parse(dagJson);

        Map<Long, TaskDefinition> tdByCode = taskDefinitions.stream()
                .collect(Collectors.toMap(TaskDefinition::getCode, Function.identity()));

        List<DagNode> newNodes = graph.getNodes().stream().map(n -> {
            TaskDefinition td = tdByCode.get(n.getTaskCode());
            DagNode node = new DagNode();
            node.setTaskCode(n.getTaskCode());
            node.setLabel(td != null ? td.getName() : n.getLabel());
            node.setPosition(n.getPosition());
            return node;
        }).collect(Collectors.toList());

        graph.setNodes(newNodes);
        return DagParser.serialize(graph);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public WorkflowDefinitionDTO createDetail(Long workspaceId, Long projectCode, WorkflowDefinitionDTO body) {
        WorkflowDefinition entity = BeanConvertUtils.convert(body, WorkflowDefinition.class);
        entity.setWorkspaceId(workspaceId);
        entity.setProjectCode(projectCode);
        return BeanConvertUtils.convert(create(entity), WorkflowDefinitionDTO.class);
    }

    public WorkflowDefinitionDTO updateDetail(Long workspaceId, Long projectCode, Long code,
                                              WorkflowDefinitionDTO body, List<TaskDefinitionDTO> taskDefs) {
        WorkflowDefinition entity = BeanConvertUtils.convert(body, WorkflowDefinition.class);
        return BeanConvertUtils.convert(
                update(workspaceId, projectCode, code, entity, taskDefs),
                WorkflowDefinitionDTO.class);
    }

    public WorkflowDefinitionDTO getByCodeDetail(Long workspaceId, Long projectCode, Long code) {
        return BeanConvertUtils.convert(getByCode(workspaceId, projectCode, code), WorkflowDefinitionDTO.class);
    }

    public IPage<WorkflowDefinitionDTO> pageByProjectCodeDetail(Long projectCode, String searchVal,
                                                                int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                pageByProjectCode(projectCode, searchVal, pageNum, pageSize),
                WorkflowDefinitionDTO.class);
    }

    public WorkflowDefinitionDTO rollbackDetail(Long workspaceId, Long projectCode, Long code,
                                                String content, Integer rollbackToVersionNo) {
        return BeanConvertUtils.convert(
                rollback(workspaceId, projectCode, code, content, rollbackToVersionNo),
                WorkflowDefinitionDTO.class);
    }

    /** Controller 用:按 (workspaceId, projectCode, code) 定位后落版本,避开 controller 直接持 entity。 */
    public void saveVersion(Long workspaceId, Long projectCode, Long code, String remark) {
        WorkflowDefinition wf = getByCode(workspaceId, projectCode, code);
        workflowVersionService.saveWorkflowVersion(wf, wf.getDagJson(), remark);
    }
}
