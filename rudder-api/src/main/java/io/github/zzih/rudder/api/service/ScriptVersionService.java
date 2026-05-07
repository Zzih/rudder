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

import io.github.zzih.rudder.common.RudderConstants;
import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.naming.GitNameUtils;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.ScriptDirDao;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.ScriptDir;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.service.script.dto.ScriptDTO;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.version.api.VersionAttributeKeys;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptVersionService {

    private static final ResourceType RESOURCE_TYPE = ResourceType.SCRIPT;

    private final ScriptService scriptService;
    private final ScriptDao scriptDao;
    private final ScriptDirDao scriptDirDao;
    private final WorkspaceDao workspaceDao;
    private final VersionService versionService;

    /** controller 路径用:按 (workspaceId, code) 定位脚本后落版本。 */
    public void saveVersion(Long workspaceId, Long code, String remark) {
        Script script = scriptService.getByCode(workspaceId, code);
        saveVersion(script, remark);
    }

    public void saveVersion(Script script, String remark) {
        log.info("保存脚本版本, scriptCode={}, remark={}", script.getCode(), remark);
        VersionRecord record = new VersionRecord();
        record.setResourceType(RESOURCE_TYPE);
        record.setResourceCode(script.getCode());
        record.setContent(script.getContent());
        record.setRemark(remark);

        // GIT provider 需要的定位信息;LOCAL 忽略
        record.getAttributes().put(VersionAttributeKeys.ORG_NAME, buildOrgName(script.getWorkspaceId()));
        record.getAttributes().put(VersionAttributeKeys.REPO_NAME, RudderConstants.IDE_REPO);
        record.getAttributes().put(VersionAttributeKeys.FILE_PATH, buildScriptFilePath(script));

        versionService.saveVersion(record);
    }

    public ScriptDTO rollback(Long workspaceId, Long code, String content, Integer rollbackToVersionNo) {
        log.info("回滚脚本版本, workspaceId={}, code={}, rollbackToVersion={}", workspaceId, code, rollbackToVersionNo);
        Script existing = scriptService.getByCode(workspaceId, code);
        existing.setContent(content);
        scriptDao.updateById(existing);
        saveVersion(existing, "Rollback to v" + rollbackToVersionNo);
        return BeanConvertUtils.convert(existing, ScriptDTO.class);
    }

    private String buildScriptFilePath(Script script) {
        String dirPath = buildDirPath(script.getWorkspaceId(), script.getDirId());
        String ext = script.getTaskType() != null ? script.getTaskType().getExt() : ".sql";
        String fileName = sanitizeFileName(script.getName()) + ext;
        if (dirPath.isEmpty()) {
            return RudderConstants.SCRIPTS_DIR + "/" + fileName;
        }
        return RudderConstants.SCRIPTS_DIR + "/" + dirPath + "/" + fileName;
    }

    private String buildDirPath(Long workspaceId, Long dirId) {
        if (dirId == null) {
            return "";
        }
        List<ScriptDir> allDirs = scriptDirDao.selectByWorkspaceId(workspaceId);
        Map<Long, ScriptDir> dirMap = allDirs.stream()
                .collect(java.util.stream.Collectors.toMap(ScriptDir::getId, d -> d));

        List<String> parts = new ArrayList<>();
        Long currentId = dirId;
        int maxDepth = 20;
        while (currentId != null && maxDepth-- > 0) {
            ScriptDir dir = dirMap.get(currentId);
            if (dir == null) {
                break;
            }
            parts.add(sanitizeFileName(dir.getName()));
            currentId = dir.getParentId();
        }
        if (maxDepth <= 0) {
            log.warn("Directory path depth exceeded for dirId={}", dirId);
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }

    private String buildOrgName(Long workspaceId) {
        Workspace ws = workspaceDao.selectById(workspaceId);
        return GitNameUtils.sanitize(ws != null ? ws.getName() : null);
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff.]", "_");
    }
}
