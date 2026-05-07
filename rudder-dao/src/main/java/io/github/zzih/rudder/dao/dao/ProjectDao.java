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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.Project;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ProjectDao {

    Project selectById(Long id);

    Project selectByCode(Long code);

    Project selectByWorkspaceIdAndCode(Long workspaceId, Long code);

    List<Project> selectByWorkspaceId(Long workspaceId);

    IPage<Project> selectPageByWorkspaceId(Long workspaceId, String searchVal, int pageNum, int pageSize);

    long countByWorkspaceIdAndName(Long workspaceId, String name);

    long countByWorkspaceIdAndNameExcludeId(Long workspaceId, String name, Long excludeId);

    int insert(Project project);

    int updateById(Project project);

    int deleteById(Long id);
}
