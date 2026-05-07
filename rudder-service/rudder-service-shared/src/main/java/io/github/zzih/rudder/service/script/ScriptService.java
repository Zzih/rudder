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

package io.github.zzih.rudder.service.script;

import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.ScriptDirDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.ScriptDir;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.service.script.dto.ScriptDTO;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptDao scriptDao;
    private final ScriptDirDao scriptDirDao;
    private final TaskDefinitionDao taskDefinitionDao;

    public Script create(Script script) {
        log.info("创建脚本, workspaceId={}, name={}, taskType={}", script.getWorkspaceId(), script.getName(),
                script.getTaskType());
        if (scriptDao.countInDirAndNameExcludeId(script.getDirId(), script.getName(), null) > 0) {
            log.warn("创建脚本失败: 名称已存在, dirId={}, name={}", script.getDirId(), script.getName());
            throw new BizException(ScriptErrorCode.SCRIPT_NAME_EXISTS);
        }
        script.setCode(CodeGenerateUtils.genCode());
        scriptDao.insert(script);
        log.info("脚本创建成功, code={}, name={}", script.getCode(), script.getName());
        return script;
    }

    public Script getByCode(Long code) {
        Script script = scriptDao.selectByCode(code);
        if (script == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_NOT_FOUND);
        }
        return script;
    }

    public Script getByCode(Long workspaceId, Long code) {
        Script script = scriptDao.selectByWorkspaceIdAndCode(workspaceId, code);
        if (script == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_NOT_FOUND);
        }
        return script;
    }

    public List<Script> listByDirId(Long dirId) {
        return scriptDao.selectByDirId(dirId);
    }

    public List<Script> listByWorkspaceId(Long workspaceId) {
        return scriptDao.selectByWorkspaceId(workspaceId);
    }

    public Script update(Long workspaceId, Long code, Script script) {
        log.info("更新脚本, workspaceId={}, code={}", workspaceId, code);
        Script existing = getByCode(workspaceId, code);

        if (script.getName() != null && !script.getName().equals(existing.getName())) {
            if (scriptDao.countInDirAndNameExcludeId(existing.getDirId(), script.getName(), existing.getId()) > 0) {
                throw new BizException(ScriptErrorCode.SCRIPT_NAME_EXISTS);
            }
            existing.setName(script.getName());
        }
        if (script.getContent() != null) {
            existing.setContent(script.getContent());
        }
        if (script.getTaskType() != null) {
            existing.setTaskType(script.getTaskType());
        }
        if (script.getDirId() != null) {
            existing.setDirId(script.getDirId());
        }

        scriptDao.updateById(existing);
        return existing;
    }

    public Script move(Long workspaceId, Long code, Long targetDirId) {
        log.info("移动脚本, workspaceId={}, code={}, targetDirId={}", workspaceId, code, targetDirId);
        Script existing = getByCode(workspaceId, code);
        if (targetDirId != null) {
            ScriptDir dir = scriptDirDao.selectByWorkspaceIdAndId(workspaceId, targetDirId);
            if (dir == null) {
                throw new NotFoundException(ScriptErrorCode.SCRIPT_DIR_NOT_FOUND);
            }
        }
        Long currentDirId = existing.getDirId();
        if ((currentDirId == null && targetDirId == null)
                || (currentDirId != null && currentDirId.equals(targetDirId))) {
            return existing;
        }
        if (scriptDao.countInDirAndNameExcludeId(targetDirId, existing.getName(), existing.getId()) > 0) {
            throw new BizException(ScriptErrorCode.SCRIPT_NAME_EXISTS);
        }
        scriptDao.updateDirId(existing.getId(), targetDirId);
        existing.setDirId(targetDirId);
        return existing;
    }

    public void delete(Long workspaceId, Long code) {
        log.info("删除脚本, workspaceId={}, code={}", workspaceId, code);
        Script existing = getByCode(workspaceId, code);
        TaskDefinition binding = taskDefinitionDao.selectByScriptCode(existing.getCode());
        if (binding != null) {
            log.info("脚本已被任务绑定, 转为TASK来源, code={}, taskCode={}", code, binding.getCode());
            existing.setSourceType(SourceType.TASK);
            scriptDao.updateById(existing);
        } else {
            scriptDao.deleteById(existing.getId());
        }
    }

    // ==================== DTO-returning methods for Controller ====================

    public ScriptDTO createDetail(Long workspaceId, ScriptDTO body) {
        Script script = BeanConvertUtils.convert(body, Script.class);
        script.setWorkspaceId(workspaceId);
        script.setSourceType(SourceType.IDE);
        return BeanConvertUtils.convert(create(script), ScriptDTO.class);
    }

    public ScriptDTO updateDetail(Long workspaceId, Long code, ScriptDTO body) {
        Script script = BeanConvertUtils.convert(body, Script.class);
        return BeanConvertUtils.convert(update(workspaceId, code, script), ScriptDTO.class);
    }

    public ScriptDTO moveDetail(Long workspaceId, Long code, Long targetDirId) {
        return BeanConvertUtils.convert(move(workspaceId, code, targetDirId), ScriptDTO.class);
    }

    public ScriptDTO getByCodeDetail(Long workspaceId, Long code) {
        return BeanConvertUtils.convert(getByCode(workspaceId, code), ScriptDTO.class);
    }

    public List<ScriptDTO> listByDirIdDetail(Long dirId) {
        return BeanConvertUtils.convertList(listByDirId(dirId), ScriptDTO.class);
    }

    public List<ScriptDTO> listByWorkspaceIdDetail(Long workspaceId) {
        return BeanConvertUtils.convertList(listByWorkspaceId(workspaceId), ScriptDTO.class);
    }

}
