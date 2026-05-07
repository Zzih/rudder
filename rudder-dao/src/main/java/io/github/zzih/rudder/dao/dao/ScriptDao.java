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

import io.github.zzih.rudder.dao.entity.Script;

import java.util.List;

public interface ScriptDao {

    Script selectById(Long id);

    Script selectByCode(Long code);

    Script selectByWorkspaceIdAndCode(Long workspaceId, Long code);

    Script selectByWorkspaceIdAndName(Long workspaceId, String name);

    List<Script> selectByCodes(java.util.Collection<Long> codes);

    List<Script> selectByDirId(Long dirId);

    List<Script> selectByWorkspaceId(Long workspaceId);

    long countInDirAndNameExcludeId(Long dirId, String name, Long excludeId);

    long countAll();

    long countByWorkspaceIds(java.util.Collection<Long> workspaceIds);

    int insert(Script script);

    int updateById(Script script);

    int updateDirId(Long id, Long dirId);

    int deleteById(Long id);
}
