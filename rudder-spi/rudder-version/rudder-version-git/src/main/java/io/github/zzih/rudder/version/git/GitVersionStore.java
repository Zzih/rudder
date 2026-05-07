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

package io.github.zzih.rudder.version.git;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskCategory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.version.api.VersionAttributeKeys;
import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.model.VersionRecord;
import io.github.zzih.rudder.version.git.client.GiteaClient;
import io.github.zzih.rudder.version.git.model.FileChange;
import io.github.zzih.rudder.version.git.model.GitRef;
import io.github.zzih.rudder.version.git.model.GiteaFileContent;
import io.github.zzih.rudder.version.git.model.GiteaFileResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GIT provider:版本内容推到 Gitea 仓库,storageRef 是 {@link GitRef} 序列化的 JSON。
 * <p>
 * 仓库组织约定(由调用方在 {@code record.attributes} 上提供):
 * <ul>
 *   <li>{@code orgName}  = 工作空间名</li>
 *   <li>{@code repoName} = 工作流的项目名 / 脚本固定 {@code "ide"}</li>
 *   <li>{@code filePath} = 仓库内文件路径(如 {@code workflows/etl_daily/dag.json})</li>
 * </ul>
 *
 * <p>多文件场景({@link #saveMultiFile}):同 commit 提交 dag.json + tasks/ + scripts/,load
 * 时按 dag.json 主路径扫同目录文件重组完整 snapshot JSON。
 */
@Slf4j
@RequiredArgsConstructor
public class GitVersionStore implements VersionStore {

    private final GiteaClient giteaClient;

    private final Set<String> ensuredOrgs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> ensuredRepos = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public String save(VersionRecord record) {
        String org = requireAttr(record, VersionAttributeKeys.ORG_NAME);
        String repo = requireAttr(record, VersionAttributeKeys.REPO_NAME);
        String filePath = requireAttr(record, VersionAttributeKeys.FILE_PATH);
        String commitMessage = record.getRemark() != null ? record.getRemark() : "Update version";

        ensureRepoExists(org, repo);

        GiteaFileResponse response = giteaClient.createOrUpdateFile(
                org, repo, filePath, record.getContent(), commitMessage);
        String commitSha = response.getCommit().getSha();

        log.info("Saved version for {}/{} -> {}/{}/{}@{}",
                record.getResourceType(), record.getResourceCode(),
                org, repo, filePath, shortSha(commitSha));

        return JsonUtils.toJson(new GitRef(commitSha, filePath, org, repo, false));
    }

    @Override
    public String saveMultiFile(VersionRecord record, Map<String, String> files) {
        String org = requireAttr(record, VersionAttributeKeys.ORG_NAME);
        String repo = requireAttr(record, VersionAttributeKeys.REPO_NAME);
        String mainFilePath = requireAttr(record, VersionAttributeKeys.FILE_PATH);
        String commitMessage = record.getRemark() != null ? record.getRemark() : "Update version";

        ensureRepoExists(org, repo);

        List<FileChange> fileChanges = new ArrayList<>();
        for (var entry : files.entrySet()) {
            var existing = giteaClient.getFileContent(org, repo, entry.getKey(), null);
            if (existing != null) {
                fileChanges.add(new FileChange(
                        "update", entry.getKey(), entry.getValue(), existing.getSha()));
            } else {
                fileChanges.add(FileChange.create(entry.getKey(), entry.getValue()));
            }
        }

        String commitSha = giteaClient.commitMultipleFiles(org, repo, commitMessage, fileChanges);
        log.info("Saved multi-file version for {}/{} -> {}/{}/{}@{} ({} files)",
                record.getResourceType(), record.getResourceCode(),
                org, repo, mainFilePath, shortSha(commitSha), files.size());

        return JsonUtils.toJson(new GitRef(commitSha, mainFilePath, org, repo, true));
    }

    @Override
    public String load(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return null;
        }
        GitRef ref = JsonUtils.fromJson(storageRef, GitRef.class);
        if (ref == null) {
            log.warn("Invalid Git storageRef: {}", storageRef);
            return null;
        }
        if (ref.isMultiFile()) {
            return fetchMultiFile(ref);
        }
        return fetchSingleFile(ref);
    }

    // ==================== Internal ====================

    private String fetchSingleFile(GitRef ref) {
        GiteaFileContent fileContent = giteaClient.getFileContent(ref.getOrg(), ref.getRepo(),
                ref.getPath(), ref.getSha());
        if (fileContent == null) {
            log.warn("File not found in Git: {}/{}/{} at {}", ref.getOrg(), ref.getRepo(),
                    ref.getPath(), ref.getSha());
            return null;
        }
        return decodeBase64(fileContent.getContent());
    }

    /**
     * 工作流多文件重组:读 dag.json + tasks/ + scripts/ + global_params.json → 完整 snapshot JSON。
     */
    private String fetchMultiFile(GitRef ref) {
        String dagFilePath = ref.getPath();
        int slash = dagFilePath.lastIndexOf('/');
        if (slash < 0) {
            log.warn("Invalid multi-file path (no parent dir): {}", dagFilePath);
            return fetchSingleFile(ref);
        }
        String workflowDir = dagFilePath.substring(0, slash);
        String org = ref.getOrg();
        String repo = ref.getRepo();
        String commitSha = ref.getSha();

        GiteaFileContent dagFile = giteaClient.getFileContent(org, repo, dagFilePath, commitSha);
        if (dagFile == null) {
            log.warn("dag.json not found: {}/{}/{} at {}", org, repo, dagFilePath, commitSha);
            return null;
        }
        String dagJson = decodeBase64(dagFile.getContent());

        String globalParams = null;
        GiteaFileContent gpFile = giteaClient.getFileContent(org, repo, workflowDir + "/global_params.json", commitSha);
        if (gpFile != null) {
            globalParams = decodeBase64(gpFile.getContent());
        }

        Map<String, String> taskConfigs = new LinkedHashMap<>();
        List<GiteaFileContent> taskFiles = giteaClient.listDirectory(org, repo, workflowDir + "/tasks", commitSha);
        for (GiteaFileContent tf : taskFiles) {
            if ("file".equals(tf.getType())) {
                GiteaFileContent taskContent = giteaClient.getFileContent(org, repo, tf.getPath(), commitSha);
                if (taskContent != null) {
                    taskConfigs.put(tf.getName(), decodeBase64(taskContent.getContent()));
                }
            }
        }

        Map<String, String> scriptContents = new LinkedHashMap<>();
        List<GiteaFileContent> scriptFiles = giteaClient.listDirectory(org, repo, workflowDir + "/scripts", commitSha);
        for (GiteaFileContent sf : scriptFiles) {
            if ("file".equals(sf.getType())) {
                GiteaFileContent scriptContent = giteaClient.getFileContent(org, repo, sf.getPath(), commitSha);
                if (scriptContent != null) {
                    scriptContents.put(sf.getName(), decodeBase64(scriptContent.getContent()));
                }
            }
        }

        return reassembleSnapshot(dagJson, globalParams, taskConfigs, scriptContents);
    }

    /**
     * 把多文件内容重组为 WorkflowSnapshot JSON 格式。
     * scripts/ 是纯文本(给人看),tasks/*.json 含 runtimeConfig(dataSourceId 等),
     * 恢复时合并为完整的 scriptContent JSON。
     */
    private String reassembleSnapshot(String dagJson, String globalParams,
                                      Map<String, String> taskConfigs,
                                      Map<String, String> scriptContents) {
        try {
            ObjectMapper mapper = JsonUtils.getObjectMapper();
            var root = mapper.createObjectNode();
            root.put("dagJson", dagJson);

            if (globalParams != null) {
                root.put("globalParams", globalParams);
            }

            var taskArray = mapper.createArrayNode();
            for (var entry : taskConfigs.entrySet()) {
                String taskName = entry.getKey().replace(".json", "");
                var taskNode = mapper.readTree(entry.getValue());

                String scriptText = findScriptByTaskName(taskName, scriptContents);

                if (scriptText != null && taskNode instanceof ObjectNode objNode) {
                    String taskType = objNode.has("taskType") ? objNode.get("taskType").asText() : "";

                    JsonNode runtimeConfigNode = null;
                    if (objNode.has("runtimeConfig")) {
                        runtimeConfigNode = objNode.get("runtimeConfig");
                        objNode.remove("runtimeConfig");
                    }

                    objNode.put("scriptContent", mergeScriptContent(scriptText, taskType, runtimeConfigNode, mapper));
                }

                taskArray.add(taskNode);
            }
            root.set("taskDefinitions", taskArray);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to reassemble workflow snapshot", e);
            return dagJson;
        }
    }

    private String findScriptByTaskName(String taskName, Map<String, String> scriptContents) {
        for (var se : scriptContents.entrySet()) {
            String baseName = se.getKey().contains(".")
                    ? se.getKey().substring(0, se.getKey().lastIndexOf('.'))
                    : se.getKey();
            if (baseName.equals(taskName)) {
                return se.getValue();
            }
        }
        return null;
    }

    /**
     * 合并纯文本脚本 + 运行时配置 → 完整 scriptContent JSON。
     */
    private String mergeScriptContent(String scriptText, String taskType,
                                      JsonNode runtimeConfigNode,
                                      ObjectMapper mapper) {
        try {
            TaskType tt = TaskType.valueOf(taskType);
            TaskCategory category = tt.getCategory();

            if (category == TaskCategory.SQL) {
                var params = (runtimeConfigNode != null)
                        ? mapper.treeToValue(runtimeConfigNode, SqlTaskParams.class)
                        : new SqlTaskParams();
                params.setSql(scriptText);
                return mapper.writeValueAsString(params);
            }

            if (category == TaskCategory.SCRIPT || category == TaskCategory.DATA_INTEGRATION) {
                var objNode = (runtimeConfigNode != null && runtimeConfigNode.isObject())
                        ? ((ObjectNode) runtimeConfigNode.deepCopy())
                        : mapper.createObjectNode();
                objNode.put("content", scriptText);
                return mapper.writeValueAsString(objNode);
            }

            return scriptText;
        } catch (Exception e) {
            log.warn("Failed to merge script content for taskType={}, falling back to raw text", taskType, e);
            return scriptText;
        }
    }

    private void ensureRepoExists(String org, String repo) {
        String key = org + "/" + repo;
        if (ensuredRepos.contains(key)) {
            return;
        }
        if (ensuredOrgs.add(org)) {
            giteaClient.ensureOrgExists(org);
        }
        giteaClient.createRepo(org, repo, "Rudder auto-created repository");
        ensuredRepos.add(key);
    }

    private static String requireAttr(VersionRecord record, String key) {
        Map<String, String> attrs = record.getAttributes();
        String value = attrs == null ? null : attrs.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "VersionRecord.attributes." + key + " is required for Git storage");
        }
        return value;
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        String cleaned = encoded.replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(cleaned), StandardCharsets.UTF_8);
    }

    private static String shortSha(String sha) {
        return sha == null ? "" : (sha.length() > 7 ? sha.substring(0, 7) : sha);
    }
}
