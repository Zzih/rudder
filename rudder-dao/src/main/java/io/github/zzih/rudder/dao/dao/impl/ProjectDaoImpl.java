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

import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.mapper.ProjectMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProjectDaoImpl implements ProjectDao {

    private final ProjectMapper projectMapper;

    @Override
    public Project selectById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public Project selectByCode(Long code) {
        return projectMapper.queryByCode(code);
    }

    @Override
    public Project selectByWorkspaceIdAndCode(Long workspaceId, Long code) {
        return projectMapper.queryByWorkspaceIdAndCode(workspaceId, code);
    }

    @Override
    public List<Project> selectByWorkspaceId(Long workspaceId) {
        return projectMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public IPage<Project> selectPageByWorkspaceId(Long workspaceId, String searchVal, int pageNum, int pageSize) {
        return projectMapper.queryPageByWorkspaceId(new Page<>(pageNum, pageSize), workspaceId, searchVal);
    }

    @Override
    public long countByWorkspaceIdAndName(Long workspaceId, String name) {
        return projectMapper.countByWorkspaceIdAndName(workspaceId, name);
    }

    @Override
    public long countByWorkspaceIdAndNameExcludeId(Long workspaceId, String name, Long excludeId) {
        return projectMapper.countByWorkspaceIdAndNameExcludeId(workspaceId, name, excludeId);
    }

    @Override
    public int insert(Project project) {
        return projectMapper.insert(project);
    }

    @Override
    public int updateById(Project project) {
        return projectMapper.updateById(project);
    }

    @Override
    public int deleteById(Long id) {
        return projectMapper.deleteById(id);
    }
}
