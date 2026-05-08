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

package io.github.zzih.rudder.publish.ariondolphin;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.bundle.EdgeBundle;
import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.ScheduleBundle;
import io.github.zzih.rudder.publish.api.bundle.TaskBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

@Slf4j
@RequiredArgsConstructor
public class ArionDolphinPublisher implements Publisher {

    private static final DateTimeFormatter DS_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArionClient arionClient;

    @Override
    public String getProvider() {
        return ArionDolphinPublisherFactory.PROVIDER;
    }

    @Override
    public void publishWorkflow(WorkflowPublishBundle bundle) {
        log.info("开始发布工作流到 DS, workflowCode={}, name={}",
                bundle.getWorkflowCode(), bundle.getWorkflowName());

        WorkflowParam workflowParam = buildWorkflowParam(bundle);

        WorkflowPublishRequest request = new WorkflowPublishRequest();
        request.setProjectName(bundle.getProjectName() != null
                ? bundle.getProjectName()
                : String.valueOf(bundle.getProjectCode()));
        request.setUserName(bundle.getUserName());
        request.setWorkflow(workflowParam);

        arionClient.publishWorkflow(request);
        log.info("工作流发布到 DS 成功, workflowCode={}", bundle.getWorkflowCode());
    }

    @Override
    public void publishProject(ProjectPublishBundle bundle) {
        log.info("开始项目发布到 DS, project={}, workflows={}",
                bundle.getProjectName(),
                bundle.getWorkflows() != null ? bundle.getWorkflows().size() : 0);

        List<WorkflowParam> workflowParams = (bundle.getWorkflows() != null
                ? bundle.getWorkflows()
                : List.<WorkflowPublishBundle>of()).stream()
                .map(this::buildWorkflowParam)
                .toList();

        ProjectPublishRequest request = new ProjectPublishRequest();
        request.setProjectName(bundle.getProjectName());
        request.setDescription(bundle.getProjectDescription());
        request.setUserName(bundle.getUserName());
        request.setWorkflows(workflowParams);

        arionClient.publishProject(request);
        log.info("项目发布到 DS 成功, project={}", bundle.getProjectName());
    }

    private WorkflowParam buildWorkflowParam(WorkflowPublishBundle bundle) {
        List<TaskBundle> tasks = bundle.getTasks() != null ? bundle.getTasks() : List.of();
        List<EdgeBundle> edges = bundle.getEdges() != null ? bundle.getEdges() : List.of();

        Map<Long, String> codeToName = tasks.stream()
                .collect(Collectors.toMap(TaskBundle::getTaskCode, TaskBundle::getName));

        List<TaskDefinitionParam> taskParams = tasks.stream()
                .map(this::buildTaskDefinitionParam)
                .toList();

        List<TaskRelationParam> taskRelations = buildTaskRelations(tasks, edges, codeToName);
        List<GlobalParam> globalParams = convertGlobalParams(bundle.getGlobalParams());
        ScheduleParam scheduleParam = convertSchedule(bundle.getSchedule());

        WorkflowParam param = new WorkflowParam();
        param.setName(bundle.getWorkflowName());
        param.setDescription(bundle.getWorkflowDescription());
        param.setTaskDefinitions(taskParams);
        param.setTaskRelations(taskRelations);
        param.setGlobalParams(globalParams);
        param.setSchedule(scheduleParam);
        return param;
    }

    private TaskDefinitionParam buildTaskDefinitionParam(TaskBundle task) {
        TaskDefinitionParam param = new TaskDefinitionParam();
        param.setName(task.getName());
        param.setDescription(task.getDescription());
        param.setTaskType(mapTaskType(task.getTaskType()));
        param.setTaskParams(buildTaskParams(task));
        param.setRetryTimes(task.getRetryTimes());
        param.setRetryInterval(task.getRetryInterval());
        param.setTimeout(task.getTimeout());
        return param;
    }

    /** Rudder TaskType → DS taskType 字符串。 */
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

    private Map<String, Object> buildTaskParams(TaskBundle task) {
        if (task.getTaskType().isControlFlow()) {
            String configJson = task.getConfigJson();
            if (configJson != null && !configJson.isBlank()) {
                return parseJsonToMap(configJson);
            }
            return Map.of();
        }

        String scriptContent = task.getScriptContent();
        if (scriptContent == null || scriptContent.isBlank()) {
            return Map.of();
        }

        Map<String, Object> params = parseJsonToMap(scriptContent);

        return switch (task.getTaskType()) {
            case HIVE_SQL, STARROCKS_SQL, MYSQL, TRINO_SQL, SPARK_SQL, FLINK_SQL -> {
                String dsType = task.getTaskType().getDatasourceType();
                if (dsType != null) {
                    params.put("type", dsType);
                }
                if (params.containsKey("dataSourceId")) {
                    params.put("datasource", params.remove("dataSourceId"));
                }
                yield params;
            }
            case PYTHON, SHELL -> {
                if (params.containsKey("content")) {
                    params.put("rawScript", params.remove("content"));
                }
                yield params;
            }
            default -> params;
        };
    }

    /** 起始节点（无入边）的 preTaskName 为 null。 */
    private List<TaskRelationParam> buildTaskRelations(List<TaskBundle> tasks, List<EdgeBundle> edges,
                                                       Map<Long, String> codeToName) {
        List<TaskRelationParam> relations = new ArrayList<>();

        Set<Long> hasIncoming = edges.stream()
                .map(EdgeBundle::getTargetTaskCode)
                .collect(Collectors.toSet());

        for (TaskBundle task : tasks) {
            if (!hasIncoming.contains(task.getTaskCode())) {
                TaskRelationParam relation = new TaskRelationParam();
                relation.setPreTaskName(null);
                relation.setPostTaskName(codeToName.get(task.getTaskCode()));
                relations.add(relation);
            }
        }

        for (EdgeBundle edge : edges) {
            TaskRelationParam relation = new TaskRelationParam();
            relation.setPreTaskName(codeToName.get(edge.getSourceTaskCode()));
            relation.setPostTaskName(codeToName.get(edge.getTargetTaskCode()));
            relations.add(relation);
        }

        return relations;
    }

    private List<GlobalParam> convertGlobalParams(List<Property> properties) {
        if (properties == null || properties.isEmpty()) {
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

    private ScheduleParam convertSchedule(ScheduleBundle schedule) {
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
