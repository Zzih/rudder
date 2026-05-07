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

import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.mapper.ScriptMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScriptDaoImpl implements ScriptDao {

    private final ScriptMapper scriptMapper;

    @Override
    public Script selectById(Long id) {
        return scriptMapper.selectById(id);
    }

    @Override
    public Script selectByCode(Long code) {
        return scriptMapper.queryByCode(code);
    }

    @Override
    public Script selectByWorkspaceIdAndCode(Long workspaceId, Long code) {
        return scriptMapper.queryByWorkspaceIdAndCode(workspaceId, code);
    }

    @Override
    public Script selectByWorkspaceIdAndName(Long workspaceId, String name) {
        return scriptMapper.queryByWorkspaceIdAndName(workspaceId, name);
    }

    @Override
    public List<Script> selectByCodes(Collection<Long> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return scriptMapper.queryByCodes(codes);
    }

    @Override
    public List<Script> selectByDirId(Long dirId) {
        return scriptMapper.queryByDirId(dirId);
    }

    @Override
    public List<Script> selectByWorkspaceId(Long workspaceId) {
        return scriptMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public long countInDirAndNameExcludeId(Long dirId, String name, Long excludeId) {
        return scriptMapper.countInDirAndNameExcludeId(dirId, name, excludeId);
    }

    @Override
    public long countAll() {
        return scriptMapper.countAll();
    }

    @Override
    public long countByWorkspaceIds(Collection<Long> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0L;
        }
        return scriptMapper.countByWorkspaceIds(workspaceIds);
    }

    @Override
    public int insert(Script script) {
        return scriptMapper.insert(script);
    }

    @Override
    public int updateById(Script script) {
        return scriptMapper.updateById(script);
    }

    @Override
    public int updateDirId(Long id, Long dirId) {
        return scriptMapper.updateDirId(id, dirId);
    }

    @Override
    public int deleteById(Long id) {
        return scriptMapper.deleteById(id);
    }
}
