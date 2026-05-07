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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.ScriptDirDao;
import io.github.zzih.rudder.dao.entity.ScriptDir;
import io.github.zzih.rudder.dao.mapper.ScriptDirMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScriptDirDaoImpl implements ScriptDirDao {

    private final ScriptDirMapper scriptDirMapper;

    @Override
    public ScriptDir selectById(Long id) {
        return scriptDirMapper.selectById(id);
    }

    @Override
    public ScriptDir selectByWorkspaceIdAndId(Long workspaceId, Long id) {
        return scriptDirMapper.queryByWorkspaceIdAndId(workspaceId, id);
    }

    @Override
    public List<ScriptDir> selectByWorkspaceId(Long workspaceId) {
        return scriptDirMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public List<ScriptDir> selectByParentId(Long parentId) {
        return scriptDirMapper.queryByParentId(parentId);
    }

    @Override
    public long countInParentAndNameExcludeId(Long workspaceId, Long parentId, String name, Long excludeId) {
        return scriptDirMapper.countInParentAndNameExcludeId(workspaceId, parentId, name, excludeId);
    }

    @Override
    public int insert(ScriptDir dir) {
        return scriptDirMapper.insert(dir);
    }

    @Override
    public int updateById(ScriptDir dir) {
        return scriptDirMapper.updateById(dir);
    }

    @Override
    public int updateParentId(Long id, Long parentId) {
        return scriptDirMapper.updateParentId(id, parentId);
    }

    @Override
    public int deleteById(Long id) {
        return scriptDirMapper.deleteById(id);
    }
}
