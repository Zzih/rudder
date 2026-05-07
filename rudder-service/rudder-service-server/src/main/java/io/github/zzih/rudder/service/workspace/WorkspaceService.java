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

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.dao.WorkspaceMemberDao;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.dao.entity.WorkspaceMember;
import io.github.zzih.rudder.service.workspace.dto.WorkspaceDTO;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceDao workspaceDao;
    private final WorkspaceMemberDao memberDao;

    private static final java.util.regex.Pattern WORKSPACE_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{1,62}$");

    public WorkspaceDTO create(WorkspaceDTO body) {
        log.info("创建工作空间, name={}", body.getName());
        validateWorkspaceName(body.getName());
        if (workspaceDao.countByName(body.getName()) > 0) {
            log.warn("创建工作空间失败: 名称已存在, name={}", body.getName());
            throw new BizException(WorkspaceErrorCode.WORKSPACE_NAME_EXISTS);
        }
        Workspace workspace = BeanConvertUtils.convert(body, Workspace.class);
        workspace.setId(null); // 防 caller 传 id；DAO insert 由数据库自动生成
        workspaceDao.insert(workspace);
        log.info("工作空间创建成功, name={}, id={}", body.getName(), workspace.getId());
        return BeanConvertUtils.convert(workspace, WorkspaceDTO.class);
    }

    public WorkspaceDTO getById(Long id) {
        Workspace workspace = workspaceDao.selectById(id);
        if (workspace == null) {
            throw new NotFoundException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND, id);
        }
        return BeanConvertUtils.convert(workspace, WorkspaceDTO.class);
    }

    public List<WorkspaceDTO> listAll() {
        return BeanConvertUtils.convertList(workspaceDao.selectAll(), WorkspaceDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkspaceDTO> pageAll(String searchVal, int pageNum,
                                                                              int pageSize) {
        return BeanConvertUtils.convertPage(workspaceDao.selectPageAll(searchVal, pageNum, pageSize),
                WorkspaceDTO.class);
    }

    public List<WorkspaceDTO> listByUserId(Long userId) {
        List<WorkspaceMember> members = memberDao.selectByUserId(userId);
        List<Long> workspaceIds = members.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return BeanConvertUtils.convertList(workspaceDao.selectByIds(workspaceIds), WorkspaceDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<WorkspaceDTO> pageByUserId(Long userId, String searchVal,
                                                                                   int pageNum, int pageSize) {
        List<WorkspaceMember> members = memberDao.selectByUserId(userId);
        List<Long> workspaceIds = members.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .toList();
        if (workspaceIds.isEmpty()) {
            return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize, 0);
        }
        return BeanConvertUtils.convertPage(workspaceDao.selectPageByIds(workspaceIds, searchVal, pageNum, pageSize),
                WorkspaceDTO.class);
    }

    public WorkspaceDTO update(Long id, WorkspaceDTO body) {
        log.info("更新工作空间, id={}, name={}", id, body.getName());
        Workspace existing = workspaceDao.selectById(id);
        if (existing == null) {
            throw new NotFoundException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND, id);
        }
        validateWorkspaceName(body.getName());
        if (workspaceDao.countByNameExcludeId(body.getName(), id) > 0) {
            throw new BizException(WorkspaceErrorCode.WORKSPACE_NAME_EXISTS);
        }
        Workspace workspace = BeanConvertUtils.convert(body, Workspace.class);
        workspace.setId(id); // 路径 id 是真理来源，避 caller 传错
        workspaceDao.updateById(workspace);
        return BeanConvertUtils.convert(workspace, WorkspaceDTO.class);
    }

    private void validateWorkspaceName(String name) {
        if (name == null || !WORKSPACE_NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(WorkspaceErrorCode.WORKSPACE_NAME_INVALID);
        }
    }

    public void delete(Long id) {
        log.info("删除工作空间, id={}", id);
        Workspace workspace = workspaceDao.selectById(id);
        if (workspace == null) {
            throw new NotFoundException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND, id);
        }
        workspaceDao.deleteById(id);
        log.info("工作空间已删除, id={}, name={}", id, workspace.getName());
    }
}
