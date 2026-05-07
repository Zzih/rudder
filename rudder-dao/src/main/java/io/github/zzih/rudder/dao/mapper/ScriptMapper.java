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

import io.github.zzih.rudder.dao.entity.Script;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ScriptMapper extends BaseMapper<Script> {

    Script queryByCode(@Param("code") Long code);

    Script queryByWorkspaceIdAndCode(@Param("workspaceId") Long workspaceId,
                                     @Param("code") Long code);

    Script queryByWorkspaceIdAndName(@Param("workspaceId") Long workspaceId,
                                     @Param("name") String name);

    List<Script> queryByCodes(@Param("codes") Collection<Long> codes);

    List<Script> queryByDirId(@Param("dirId") Long dirId);

    List<Script> queryByWorkspaceId(@Param("workspaceId") Long workspaceId);

    long countInDirAndNameExcludeId(@Param("dirId") Long dirId,
                                    @Param("name") String name,
                                    @Param("excludeId") Long excludeId);

    int updateDirId(@Param("id") Long id,
                    @Param("dirId") Long dirId);

    long countAll();

    long countByWorkspaceIds(@Param("workspaceIds") Collection<Long> workspaceIds);
}
