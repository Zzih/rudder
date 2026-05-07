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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.Project;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    Project queryByCode(@Param("code") Long code);

    Project queryByWorkspaceIdAndCode(@Param("workspaceId") Long workspaceId,
                                      @Param("code") Long code);

    List<Project> queryByWorkspaceId(@Param("workspaceId") Long workspaceId);

    IPage<Project> queryPageByWorkspaceId(IPage<Project> page,
                                          @Param("workspaceId") Long workspaceId,
                                          @Param("searchVal") String searchVal);

    long countByWorkspaceIdAndName(@Param("workspaceId") Long workspaceId,
                                   @Param("name") String name);

    long countByWorkspaceIdAndNameExcludeId(@Param("workspaceId") Long workspaceId,
                                            @Param("name") String name,
                                            @Param("excludeId") Long excludeId);
}
