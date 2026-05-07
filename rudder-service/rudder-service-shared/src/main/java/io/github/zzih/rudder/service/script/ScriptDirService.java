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
import io.github.zzih.rudder.dao.dao.ScriptDirDao;
import io.github.zzih.rudder.dao.entity.ScriptDir;
import io.github.zzih.rudder.service.script.dto.ScriptDirDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptDirService {

    private final ScriptDirDao scriptDirDao;

    public ScriptDirDTO createDetail(Long workspaceId, ScriptDirDTO body) {
        log.info("创建脚本目录, workspaceId={}, parentId={}, name={}",
                workspaceId, body.getParentId(), body.getName());
        ScriptDir dir = BeanConvertUtils.convert(body, ScriptDir.class);
        dir.setId(null);
        dir.setWorkspaceId(workspaceId);
        scriptDirDao.insert(dir);
        return BeanConvertUtils.convert(dir, ScriptDirDTO.class);
    }

    public List<ScriptDir> listByWorkspaceId(Long workspaceId) {
        return scriptDirDao.selectByWorkspaceId(workspaceId);
    }

    public List<ScriptDirDTO> listByWorkspaceIdDetail(Long workspaceId) {
        return BeanConvertUtils.convertList(listByWorkspaceId(workspaceId), ScriptDirDTO.class);
    }

    public ScriptDirDTO updateDetail(Long workspaceId, Long id, ScriptDirDTO body) {
        log.info("更新脚本目录, workspaceId={}, id={}, name={}", workspaceId, id, body.getName());
        ScriptDir dir = scriptDirDao.selectByWorkspaceIdAndId(workspaceId, id);
        if (dir == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_DIR_NOT_FOUND);
        }
        dir.setName(body.getName());
        scriptDirDao.updateById(dir);
        return BeanConvertUtils.convert(dir, ScriptDirDTO.class);
    }

    public ScriptDirDTO moveDetail(Long workspaceId, Long id, ScriptDirDTO body) {
        Long targetParentId = body.getParentId();
        log.info("移动脚本目录, workspaceId={}, id={}, targetParentId={}", workspaceId, id, targetParentId);
        ScriptDir dir = scriptDirDao.selectByWorkspaceIdAndId(workspaceId, id);
        if (dir == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_DIR_NOT_FOUND);
        }
        if (targetParentId != null) {
            Map<Long, Long> parentOf = new HashMap<>();
            for (ScriptDir d : scriptDirDao.selectByWorkspaceId(workspaceId)) {
                parentOf.put(d.getId(), d.getParentId());
            }
            if (!parentOf.containsKey(targetParentId)) {
                throw new NotFoundException(ScriptErrorCode.SCRIPT_DIR_NOT_FOUND);
            }
            for (Long cursor = targetParentId; cursor != null; cursor = parentOf.get(cursor)) {
                if (cursor.equals(id)) {
                    throw new BizException(ScriptErrorCode.SCRIPT_DIR_MOVE_CYCLE);
                }
            }
        }
        Long currentParentId = dir.getParentId();
        if ((currentParentId == null && targetParentId == null)
                || (currentParentId != null && currentParentId.equals(targetParentId))) {
            return BeanConvertUtils.convert(dir, ScriptDirDTO.class);
        }
        if (scriptDirDao.countInParentAndNameExcludeId(workspaceId, targetParentId, dir.getName(), dir.getId()) > 0) {
            throw new BizException(ScriptErrorCode.SCRIPT_DIR_NAME_EXISTS);
        }
        scriptDirDao.updateParentId(dir.getId(), targetParentId);
        dir.setParentId(targetParentId);
        return BeanConvertUtils.convert(dir, ScriptDirDTO.class);
    }

    public void delete(Long workspaceId, Long id) {
        log.info("删除脚本目录, workspaceId={}, id={}", workspaceId, id);
        ScriptDir dir = scriptDirDao.selectByWorkspaceIdAndId(workspaceId, id);
        if (dir == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_DIR_NOT_FOUND);
        }
        scriptDirDao.deleteById(id);
    }
}
