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

import io.github.zzih.rudder.common.RudderConstants;
import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.GitNameUtils;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.task.api.task.enums.TaskCategory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.version.api.VersionAttributeKeys;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowVersionService {

    private final WorkspaceDao workspaceDao;
    private final ProjectDao projectDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ScriptDao scriptDao;
    private final VersionService versionService;

    /**
     * 保存工作流完整版本快照。
     * LOCAL 模式:storage_ref 直接是 snapshotJson;
     * GIT 模式:多文件 commit(dag.json + tasks/*.json + scripts/*),storage_ref 是 GitRef JSON。
     */
    public VersionRecord saveWorkflowVersion(WorkflowDefinition workflow, String dagJson, String remark) {
        List<TaskDefinition> tasks = taskDefinitionDao.selectByWorkflowDefinitionCode(workflow.getCode());
        Map<Long, Script> scriptMap = loadScripts(tasks);

        String snapshotJson = buildSnapshotJson(workflow, dagJson, tasks, scriptMap);

        String workflowDir = RudderConstants.WORKFLOW_DEFINITIONS_DIR + "/" + sanitizeName(workflow.getName());
        Map<String, String> files = buildMultiFileMap(workflowDir, dagJson, workflow, tasks, scriptMap);

        VersionRecord vr = new VersionRecord();
        vr.setResourceType(ResourceType.WORKFLOW);
        vr.setResourceCode(workflow.getCode());
        vr.setContent(snapshotJson);
        vr.setRemark(remark);
        vr.getAttributes().put(VersionAttributeKeys.ORG_NAME, buildOrgName(workflow.getWorkspaceId()));
        vr.getAttributes().put(VersionAttributeKeys.REPO_NAME, buildRepoName(workflow.getProjectCode()));
        vr.getAttributes().put(VersionAttributeKeys.FILE_PATH, workflowDir + "/" + RudderConstants.WORKFLOW_DAG_FILE);

        return versionService.saveMultiFileVersion(vr, files);
    }

    /**
     * 构建多文件映射：文件路径 → 文件内容。
     */
    private Map<String, String> buildMultiFileMap(String dir, String dagJson, WorkflowDefinition workflow,
                                                  List<TaskDefinition> tasks, Map<Long, Script> scriptMap) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put(dir + "/" + RudderConstants.WORKFLOW_DAG_FILE, formatJson(dagJson));

        // 2. global_params.json — 全局参数
        if (workflow.getGlobalParams() != null && !workflow.getGlobalParams().isBlank()) {
            files.put(dir + "/global_params.json", formatJson(workflow.getGlobalParams()));
        }

        for (TaskDefinition td : tasks) {
            String taskName = sanitizeName(td.getName());

            Script script = td.getScriptCode() != null ? scriptMap.get(td.getScriptCode()) : null;

            // 2. tasks/{taskName}.json — 任务配置 + 运行时参数（dataSourceId 等）
            Map<String, Object> taskConfig = new LinkedHashMap<>();
            taskConfig.put("code", td.getCode());
            taskConfig.put("name", td.getName());
            taskConfig.put("taskType", td.getTaskType() != null ? td.getTaskType().name() : null);
            taskConfig.put("scriptCode", td.getScriptCode());
            taskConfig.put("configJson", td.getConfigJson());
            taskConfig.put("description", td.getDescription());
            taskConfig.put("inputParams", td.getInputParams());
            taskConfig.put("outputParams", td.getOutputParams());
            taskConfig.put("priority", td.getPriority());
            taskConfig.put("delayTime", td.getDelayTime());
            taskConfig.put("retryTimes", td.getRetryTimes());
            taskConfig.put("retryInterval", td.getRetryInterval());
            taskConfig.put("timeout", td.getTimeout());
            taskConfig.put("timeoutEnabled", td.getTimeoutEnabled());
            taskConfig.put("isEnabled", td.getIsEnabled());

            // 从 scriptContent JSON 提取运行时配置（dataSourceId/executionMode 等）
            if (script != null && script.getContent() != null) {
                Map<String, Object> runtimeConfig = extractRuntimeConfig(script.getContent());
                if (!runtimeConfig.isEmpty()) {
                    taskConfig.put("runtimeConfig", runtimeConfig);
                }
            }

            files.put(dir + "/tasks/" + taskName + ".json", JsonUtils.toPrettyJson(taskConfig));

            // 3. scripts/{taskName}{ext} — 纯脚本内容（给人看）
            if (script != null && script.getContent() != null) {
                String ext = getScriptExtension(td.getTaskType());
                String readableContent = extractReadableContent(script.getContent(), td.getTaskType());
                files.put(dir + "/scripts/" + taskName + ext, readableContent);
            }
        }

        return files;
    }

    /**
     * 从脚本 JSON 中提取人类可读内容。
     * SQL → 纯 SQL 文本
     * Python/Shell → 纯脚本文本
     * JAR → 格式化 JSON
     */
    private String extractReadableContent(String contentJson, TaskType taskType) {
        if (contentJson == null) {
            return "";
        }
        try {
            var parsed = JsonUtils.fromJson(contentJson, Map.class);
            if (taskType != null) {
                TaskCategory category = taskType.getCategory();
                if (category == TaskCategory.SQL) {
                    Object sql = parsed.get("sql");
                    return sql != null ? sql.toString() : contentJson;
                }
                if (category == TaskCategory.SCRIPT || category == TaskCategory.DATA_INTEGRATION) {
                    Object content = parsed.get("content");
                    return content != null ? content.toString() : contentJson;
                }
            }
            // JAR 等：格式化 JSON
            return JsonUtils.toPrettyJson(parsed);
        } catch (Exception e) {
            return contentJson;
        }
    }

    /** 从 scriptContent JSON 切出运行时配置(dataSourceId/executionMode/...);版本恢复时和文本合并。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRuntimeConfig(String contentJson) {
        Map<String, Object> config = new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = JsonUtils.fromJson(contentJson, Map.class);
            for (var entry : parsed.entrySet()) {
                String key = entry.getKey();
                if (!"sql".equals(key) && !"content".equals(key)) {
                    config.put(key, entry.getValue());
                }
            }
        } catch (Exception e) {
            // 旧版本快照可能格式漂移;静默 fallback 会让恢复出来的版本丢 dataSourceId,WARN 留追溯
            log.warn("extractRuntimeConfig failed, returning empty config: {}", e.getMessage());
        }
        return config;
    }

    private String getScriptExtension(TaskType taskType) {
        if (taskType == null) {
            return ".txt";
        }
        return taskType.getExt().isEmpty() ? ".json" : taskType.getExt();
    }

    // ==================== 快照构建（DB 模式 + 读取回来的格式）====================

    private String buildSnapshotJson(WorkflowDefinition workflow, String dagJson,
                                     List<TaskDefinition> tasks, Map<Long, Script> scriptMap) {
        List<TaskSnapshot> taskSnapshots = new ArrayList<>();
        for (TaskDefinition td : tasks) {
            TaskSnapshot ts = new TaskSnapshot();
            ts.setCode(td.getCode());
            ts.setName(td.getName());
            ts.setTaskType(td.getTaskType() != null ? td.getTaskType().name() : null);
            ts.setScriptCode(td.getScriptCode());
            Script script = td.getScriptCode() != null ? scriptMap.get(td.getScriptCode()) : null;
            ts.setScriptContent(script != null ? script.getContent() : null);
            ts.setConfigJson(td.getConfigJson());
            ts.setDescription(td.getDescription());
            ts.setInputParams(td.getInputParams());
            ts.setOutputParams(td.getOutputParams());
            ts.setPriority(td.getPriority());
            ts.setDelayTime(td.getDelayTime());
            ts.setRetryTimes(td.getRetryTimes());
            ts.setRetryInterval(td.getRetryInterval());
            ts.setTimeout(td.getTimeout());
            ts.setTimeoutEnabled(td.getTimeoutEnabled());
            ts.setIsEnabled(td.getIsEnabled());
            taskSnapshots.add(ts);
        }

        WorkflowSnapshot snapshot = new WorkflowSnapshot();
        snapshot.setDagJson(dagJson);
        snapshot.setGlobalParams(workflow.getGlobalParams());
        snapshot.setTaskDefinitions(taskSnapshots);
        return JsonUtils.toJson(snapshot);
    }

    private Map<Long, Script> loadScripts(List<TaskDefinition> tasks) {
        List<Long> scriptCodes = tasks.stream()
                .map(TaskDefinition::getScriptCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Script> map = new LinkedHashMap<>();
        if (!scriptCodes.isEmpty()) {
            for (Script s : scriptDao.selectByCodes(scriptCodes)) {
                map.put(s.getCode(), s);
            }
        }
        return map;
    }

    // ==================== 路径构建 ====================

    private String buildOrgName(Long workspaceId) {
        Workspace ws = workspaceDao.selectById(workspaceId);
        return GitNameUtils.sanitize(ws != null ? ws.getName() : null);
    }

    private String buildRepoName(Long projectCode) {
        Project project = projectDao.selectByCode(projectCode);
        return GitNameUtils.sanitize(project != null ? project.getName() : null);
    }

    /** 用作工作流目录名和脚本文件名 —— 语义是"文件路径段"，不是 Gitea ref 名。 */
    private static String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9_\\-]", "-");
    }

    private static String formatJson(String json) {
        try {
            Object parsed = JsonUtils.fromJson(json, Object.class);
            return JsonUtils.toPrettyJson(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    // ==================== Snapshot Models ====================

    @Data
    public static class WorkflowSnapshot {

        private String dagJson;
        private String globalParams;
        private List<TaskSnapshot> taskDefinitions;
    }

    @Data
    public static class TaskSnapshot {

        private Long code;
        private String name;
        private String taskType;
        private Long scriptCode;
        private String scriptContent;
        private String configJson;
        private String description;
        private String inputParams;
        private String outputParams;
        private String priority;
        private Integer delayTime;
        private Integer retryTimes;
        private Integer retryInterval;
        private Integer timeout;
        private Boolean timeoutEnabled;
        private Boolean isEnabled;
    }
}
