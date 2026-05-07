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

import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.dao.mapper.WorkspaceMemberMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WorkspaceMemberDaoImpl implements WorkspaceMemberDao {

    private final WorkspaceMemberMapper memberMapper;

    @Override
    public WorkspaceMember selectByWorkspaceIdAndUserId(Long workspaceId, Long userId) {
        return memberMapper.queryByWorkspaceIdAndUserId(workspaceId, userId);
    }

    @Override
    public List<WorkspaceMember> selectByWorkspaceId(Long workspaceId) {
        return memberMapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public List<WorkspaceMember> selectByWorkspaceIdAndRole(Long workspaceId, String role) {
        return memberMapper.queryByWorkspaceIdAndRole(workspaceId, role);
    }

    @Override
    public List<WorkspaceMember> selectByUserId(Long userId) {
        return memberMapper.queryByUserId(userId);
    }

    @Override
    public int insert(WorkspaceMember member) {
        return memberMapper.insert(member);
    }

    @Override
    public int updateById(WorkspaceMember member) {
        return memberMapper.updateById(member);
    }

    @Override
    public int deleteById(Long id) {
        return memberMapper.deleteById(id);
    }
}
