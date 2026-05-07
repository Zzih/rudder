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

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowSchedule;
import io.github.zzih.rudder.service.workflow.dag.DagEdge;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import io.github.zzih.arion.dolphin.client.ArionClient;
import io.github.zzih.arion.dolphin.domain.qo.GlobalParam;
import io.github.zzih.arion.dolphin.domain.qo.ProjectPublishRequest;
import io.github.zzih.arion.dolphin.domain.qo.ScheduleParam;
import io.github.zzih.arion.dolphin.domain.qo.TaskDefinitionParam;
import io.github.zzih.arion.dolphin.domain.qo.TaskRelationParam;
import io.github.zzih.arion.dolphin.domain.qo.WorkflowParam;
import io.github.zzih.arion.dolphin.domain.qo.WorkflowPublishRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 Rudder 工作流发布到 DolphinScheduler（通过 Arion 网关）。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "arion-dolphin.client", name = "url")
@RequiredArgsConstructor
public class ArionDolphinPublishService {

    private static final DateTimeFormatter DS_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArionClient arionClient;
    private final ProjectDao projectDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ScriptDao scriptDao;
    private final WorkflowScheduleService workflowScheduleService;

    /**
     * 发布工作流到 DolphinScheduler。
     *
     * @param workflow 工作流定义
     * @param userName 发布用户名（对应 DS 的用户）
     */
    public void publish(WorkflowDefinition workflow, String userName) {
        log.info("开始发布工作流到 DS, workflowCode={}, name={}", workflow.getCode(), workflow.getName());

        Project project = projectDao.selectByCode(workflow.getProjectCode());
        String projectName = project != null ? project.getName() : String.valueOf(workflow.getProjectCode());

        WorkflowParam workflowParam = buildWorkflowParam(workflow);

        WorkflowPublishRequest request = new WorkflowPublishRequest();
        request.setProjectName(projectName);
        request.setUserName(userName);
        request.setWorkflow(workflowParam);

        arionClient.publishWorkflow(request);
        log.info("工作流发布到 DS 成功, workflowCode={}", workflow.getCode());
    }

    /**
     * 项目级批量发布到 DolphinScheduler，一次请求包含多个工作流。
     */
    public void publishProject(List<WorkflowDefinition> workflows, String userName,
                               String projectName, String projectDescription) {
        log.info("开始项目发布到 DS, project={}, workflows={}", projectName, workflows.size());

        List<WorkflowParam> workflowParams = workflows.stream()
                .map(this::buildWorkflowParam)
                .toList();

        ProjectPublishRequest request = new ProjectPublishRequest();
        request.setProjectName(projectName);
        request.setDescription(projectDescription);
        request.setUserName(userName);
        request.setWorkflows(workflowParams);

        arionClient.publishProject(request);
        log.info("项目发布到 DS 成功, project={}", projectName);
    }

    private WorkflowParam buildWorkflowParam(WorkflowDefinition workflow) {
        DagGraph dag = DagParser.parse(workflow.getDagJson());

        List<TaskDefinition> taskDefs = taskDefinitionDao.selectByWorkflowDefinitionCode(workflow.getCode());
        Map<Long, TaskDefinition> taskDefByCode = taskDefs.stream()
                .collect(Collectors.toMap(TaskDefinition::getCode, Function.identity()));

        // 批量查询关联脚本
        Set<Long> scriptCodes = taskDefs.stream()
                .map(TaskDefinition::getScriptCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Script> scriptMap = scriptCodes.isEmpty()
                ? Map.of()
                : scriptDao.selectByCodes(scriptCodes).stream()
                        .collect(Collectors.toMap(Script::getCode, Function.identity()));

        // taskCode → taskName 映射（用于边转换）
        Map<Long, String> codeToName = taskDefs.stream()
                .collect(Collectors.toMap(TaskDefinition::getCode, TaskDefinition::getName));

        // 构建 taskDefinitions
        List<TaskDefinitionParam> taskParams = new ArrayList<>();
        for (DagNode node : dag.getNodes()) {
            TaskDefinition td = taskDefByCode.get(node.getTaskCode());
            if (td == null) {
                continue;
            }
            taskParams.add(buildTaskDefinitionParam(td, scriptMap.get(td.getScriptCode())));
        }

        // 构建 taskRelations
        List<TaskRelationParam> taskRelations = buildTaskRelations(dag, codeToName);

        // 构建 globalParams
        List<GlobalParam> globalParams = convertGlobalParams(workflow.getGlobalParams());

        // 构建 schedule
        WorkflowSchedule schedule = workflowScheduleService.getByWorkflowDefinitionCode(workflow.getCode());
        ScheduleParam scheduleParam = convertSchedule(schedule);

        WorkflowParam param = new WorkflowParam();
        param.setName(workflow.getName());
        param.setDescription(workflow.getDescription());
        param.setTaskDefinitions(taskParams);
        param.setTaskRelations(taskRelations);
        param.setGlobalParams(globalParams);
        param.setSchedule(scheduleParam);
        return param;
    }

    private TaskDefinitionParam buildTaskDefinitionParam(TaskDefinition td, Script script) {
        TaskDefinitionParam param = new TaskDefinitionParam();
        param.setName(td.getName());
        param.setDescription(td.getDescription());
        param.setTaskType(mapTaskType(td.getTaskType()));
        param.setTaskParams(buildTaskParams(td, script));
        param.setRetryTimes(td.getRetryTimes());
        param.setRetryInterval(td.getRetryInterval());
        param.setTimeout(td.getTimeout());
        return param;
    }

    /**
     * Rudder TaskType → DS taskType 字符串。
     */
    private String mapTaskType(TaskType taskType) {
        return switch (taskType) {
            case HIVE_SQL, STARROCKS_SQL, MYSQL, DORIS_SQL, POSTGRES_SQL, CLICKHOUSE_SQL,
                    TRINO_SQL, SPARK_SQL, FLINK_SQL ->
                "SQL";
            case SPARK_JAR -> "SPARK";
            case FLINK_JAR -> "FLINK";
            case PYTHON -> "PYTHON";
            case SHELL -> "SHELL";
            case HTTP -> "HTTP";
            case SEATUNNEL -> "SEATUNNEL";
            case CONDITION -> "CONDITIONS";
            case SUB_WORKFLOW -> "SUB_PROCESS";
            case SWITCH -> "SWITCH";
            case DEPENDENT -> "DEPENDENT";
        };
    }

    /**
     * 根据任务类型构建 DS 兼容的 taskParams。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildTaskParams(TaskDefinition td, Script script) {
        String scriptContent = script != null ? script.getContent() : null;

        // 控制流任务：直接用 configJson
        if (td.getTaskType().isControlFlow()) {
            if (td.getConfigJson() != null && !td.getConfigJson().isBlank()) {
                return parseJsonToMap(td.getConfigJson());
            }
            return Map.of();
        }

        if (scriptContent == null || scriptContent.isBlank()) {
            return Map.of();
        }

        Map<String, Object> params = parseJsonToMap(scriptContent);

        return switch (td.getTaskType()) {
            case HIVE_SQL, STARROCKS_SQL, MYSQL, TRINO_SQL, SPARK_SQL, FLINK_SQL -> {
                // 添加 DS 需要的 type 字段（数据源类型）
                String dsType = td.getTaskType().getDatasourceType();
                if (dsType != null) {
                    params.put("type", dsType);
                }
                // datasource 字段（DS 用数字 ID）
                if (params.containsKey("dataSourceId")) {
                    params.put("datasource", params.remove("dataSourceId"));
                }
                yield params;
            }
            case PYTHON, SHELL -> {
                // DS 使用 rawScript 字段
                if (params.containsKey("content")) {
                    params.put("rawScript", params.remove("content"));
                }
                yield params;
            }
            case SPARK_JAR, FLINK_JAR, SEATUNNEL -> params;
            default -> params;
        };
    }

    /**
     * 将 DAG 的边转换为 DS 的 TaskRelation（基于 task name）。
     * 起始节点的 preTaskName 为 null。
     */
    private List<TaskRelationParam> buildTaskRelations(DagGraph dag, Map<Long, String> codeToName) {
        List<TaskRelationParam> relations = new ArrayList<>();

        // 找出所有起始节点（没有入边）
        Set<Long> hasIncoming = dag.getEdges().stream()
                .map(DagEdge::getTarget)
                .collect(Collectors.toSet());

        for (DagNode node : dag.getNodes()) {
            if (!hasIncoming.contains(node.getTaskCode())) {
                TaskRelationParam relation = new TaskRelationParam();
                relation.setPreTaskName(null);
                relation.setPostTaskName(codeToName.get(node.getTaskCode()));
                relations.add(relation);
            }
        }

        // 每条边对应一个 relation
        for (DagEdge edge : dag.getEdges()) {
            TaskRelationParam relation = new TaskRelationParam();
            relation.setPreTaskName(codeToName.get(edge.getSource()));
            relation.setPostTaskName(codeToName.get(edge.getTarget()));
            relations.add(relation);
        }

        return relations;
    }

    private List<GlobalParam> convertGlobalParams(String globalParamsJson) {
        if (globalParamsJson == null || globalParamsJson.isBlank()) {
            return List.of();
        }
        List<Property> properties = JsonUtils.toList(globalParamsJson, Property.class);
        if (properties == null) {
            return List.of();
        }
        return properties.stream().map(p -> {
            GlobalParam gp = new GlobalParam();
            gp.setProp(p.getProp());
            gp.setValue(p.getValue());
            gp.setDirect(p.getDirect() != null ? p.getDirect().name() : null);
            gp.setType(p.getType() != null ? p.getType().name() : null);
            return gp;
        }).toList();
    }

    private ScheduleParam convertSchedule(WorkflowSchedule schedule) {
        if (schedule == null || schedule.getCronExpression() == null) {
            return null;
        }
        ScheduleParam param = new ScheduleParam();
        param.setCrontab(schedule.getCronExpression());
        param.setTimezoneId(schedule.getTimezone() != null ? schedule.getTimezone() : "Asia/Shanghai");
        if (schedule.getStartTime() != null) {
            param.setStartTime(schedule.getStartTime().format(DS_TIME_FMT));
        }
        if (schedule.getEndTime() != null) {
            param.setEndTime(schedule.getEndTime().format(DS_TIME_FMT));
        }
        return param;
    }

    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return JsonUtils.fromJson(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", json, e);
            return new LinkedHashMap<>();
        }
    }
}
