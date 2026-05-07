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

import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.dao.mapper.WorkspaceMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WorkspaceDaoImpl implements WorkspaceDao {

    private final WorkspaceMapper workspaceMapper;

    @Override
    public Workspace selectById(Long id) {
        return workspaceMapper.selectById(id);
    }

    @Override
    public List<Workspace> selectAll() {
        return workspaceMapper.selectList(null);
    }

    @Override
    public IPage<Workspace> selectPageAll(String searchVal, int pageNum, int pageSize) {
        return workspaceMapper.queryPageAll(new Page<>(pageNum, pageSize), searchVal);
    }

    @Override
    public List<Workspace> selectByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return workspaceMapper.selectBatchIds(ids);
    }

    @Override
    public IPage<Workspace> selectPageByIds(List<Long> ids, String searchVal, int pageNum, int pageSize) {
        if (ids == null || ids.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        return workspaceMapper.queryPageByIds(new Page<>(pageNum, pageSize), ids, searchVal);
    }

    @Override
    public long countByName(String name) {
        return workspaceMapper.countByName(name);
    }

    @Override
    public long countByNameExcludeId(String name, Long excludeId) {
        return workspaceMapper.countByNameExcludeId(name, excludeId);
    }

    @Override
    public long countAll() {
        return workspaceMapper.countAll();
    }

    @Override
    public int insert(Workspace workspace) {
        return workspaceMapper.insert(workspace);
    }

    @Override
    public int updateById(Workspace workspace) {
        return workspaceMapper.updateById(workspace);
    }

    @Override
    public int deleteById(Long id) {
        return workspaceMapper.deleteById(id);
    }
}
