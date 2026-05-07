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

package io.github.zzih.rudder.service.workspace;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.workspace.dto.MemberDTO;
import io.github.zzih.rudder.service.workspace.event.WorkspaceMemberChangedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final WorkspaceMemberDao memberDao;
    private final UserDao userDao;
    private final WorkspaceDao workspaceDao;
    private final ApplicationEventPublisher eventPublisher;

    public MemberDTO addMember(Long workspaceId, Long userId, String role) {
        log.info("添加成员, workspaceId={}, userId={}, role={}", workspaceId, userId, role);
        validateRole(role);
        WorkspaceMember existing = memberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
        if (existing != null) {
            log.warn("添加成员失败: 成员已存在, workspaceId={}, userId={}", workspaceId, userId);
            throw new BizException(WorkspaceErrorCode.MEMBER_EXISTS);
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        memberDao.insert(member);
        eventPublisher.publishEvent(WorkspaceMemberChangedEvent.added(workspaceId, userId, role));
        return enrich(BeanConvertUtils.convert(member, MemberDTO.class));
    }

    public void removeMember(Long workspaceId, Long userId) {
        log.info("移除成员, workspaceId={}, userId={}", workspaceId, userId);
        WorkspaceMember member = memberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
        if (member == null) {
            throw new NotFoundException(WorkspaceErrorCode.MEMBER_NOT_FOUND);
        }
        memberDao.deleteById(member.getId());
        eventPublisher.publishEvent(WorkspaceMemberChangedEvent.removed(workspaceId, userId, member.getRole()));
    }

    public void updateRole(Long workspaceId, Long userId, String role) {
        log.info("更新成员角色, workspaceId={}, userId={}, role={}", workspaceId, userId, role);
        validateRole(role);
        WorkspaceMember member = memberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
        if (member == null) {
            throw new NotFoundException(WorkspaceErrorCode.MEMBER_NOT_FOUND);
        }
        String oldRole = member.getRole();
        if (Objects.equals(oldRole, role)) {
            return; // 角色未变化，不发事件
        }
        member.setRole(role);
        memberDao.updateById(member);
        eventPublisher.publishEvent(WorkspaceMemberChangedEvent.updated(workspaceId, userId, oldRole, role));
    }

    public List<MemberDTO> listByWorkspaceId(Long workspaceId) {
        return enrichList(BeanConvertUtils.convertList(memberDao.selectByWorkspaceId(workspaceId), MemberDTO.class));
    }

    public List<MemberDTO> listByUserId(Long userId) {
        return enrichList(BeanConvertUtils.convertList(memberDao.selectByUserId(userId), MemberDTO.class));
    }

    public WorkspaceMember getMember(Long workspaceId, Long userId) {
        return memberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
    }

    public boolean hasRole(Long workspaceId, Long userId, RoleType minRole) {
        WorkspaceMember member = memberDao.selectByWorkspaceIdAndUserId(workspaceId, userId);
        if (member == null) {
            return false;
        }
        try {
            RoleType memberRole = RoleType.of(member.getRole());
            return memberRole.getLevel() >= minRole.getLevel();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validateRole(String role) {
        try {
            RoleType.of(role);
        } catch (IllegalArgumentException e) {
            throw new BizException(WorkspaceErrorCode.INVALID_ROLE);
        }
    }

    private List<MemberDTO> enrichList(List<MemberDTO> list) {
        if (list.isEmpty()) {
            return list;
        }
        Set<Long> userIds = list.stream()
                .map(MemberDTO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> workspaceIds = list.stream()
                .map(MemberDTO::getWorkspaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNames = userIds.isEmpty()
                ? Map.of()
                : userDao.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        Map<Long, String> workspaceNames = workspaceIds.isEmpty()
                ? Map.of()
                : workspaceDao.selectByIds(new ArrayList<>(workspaceIds)).stream()
                        .collect(Collectors.toMap(Workspace::getId, Workspace::getName));
        for (MemberDTO dto : list) {
            if (dto.getUserId() != null) {
                dto.setUsername(userNames.get(dto.getUserId()));
            }
            if (dto.getWorkspaceId() != null) {
                dto.setWorkspaceName(workspaceNames.get(dto.getWorkspaceId()));
            }
        }
        return list;
    }

    private MemberDTO enrich(MemberDTO dto) {
        if (dto.getUserId() != null) {
            User user = userDao.selectById(dto.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
            }
        }
        if (dto.getWorkspaceId() != null) {
            Workspace ws = workspaceDao.selectById(dto.getWorkspaceId());
            if (ws != null) {
                dto.setWorkspaceName(ws.getName());
            }
        }
        return dto;
    }
}
