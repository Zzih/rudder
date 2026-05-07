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
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.service.workspace.dto.ProjectDTO;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectDao projectDao;
    private final UserDao userDao;

    public ProjectDTO create(Long workspaceId, ProjectDTO body) {
        log.info("创建项目, workspaceId={}, name={}", workspaceId, body.getName());
        if (projectDao.countByWorkspaceIdAndName(workspaceId, body.getName()) > 0) {
            log.warn("创建项目失败: 名称已存在, workspaceId={}, name={}", workspaceId, body.getName());
            throw new BizException(WorkspaceErrorCode.PROJECT_NAME_EXISTS);
        }
        Project project = BeanConvertUtils.convert(body, Project.class);
        project.setId(null);
        project.setWorkspaceId(workspaceId); // 路径参数为准
        project.setCode(CodeGenerateUtils.genCode());
        projectDao.insert(project);
        return BeanConvertUtils.convert(project, ProjectDTO.class);
    }

    public ProjectDTO getByCode(Long code) {
        Project project = projectDao.selectByCode(code);
        if (project == null) {
            throw new NotFoundException(WorkspaceErrorCode.PROJECT_NOT_FOUND, code);
        }
        return enrich(BeanConvertUtils.convert(project, ProjectDTO.class));
    }

    public ProjectDTO getByCode(Long workspaceId, Long code) {
        Project project = projectDao.selectByWorkspaceIdAndCode(workspaceId, code);
        if (project == null) {
            throw new NotFoundException(WorkspaceErrorCode.PROJECT_NOT_FOUND, code);
        }
        return enrich(BeanConvertUtils.convert(project, ProjectDTO.class));
    }

    public List<ProjectDTO> listByWorkspaceId(Long workspaceId) {
        List<ProjectDTO> list =
                BeanConvertUtils.convertList(projectDao.selectByWorkspaceId(workspaceId), ProjectDTO.class);
        if (list.isEmpty()) {
            return list;
        }
        Set<Long> userIds = list.stream()
                .map(ProjectDTO::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNames = userIds.isEmpty()
                ? Map.of()
                : userDao.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        for (ProjectDTO dto : list) {
            if (dto.getCreatedBy() != null) {
                dto.setCreatedByUsername(userNames.get(dto.getCreatedBy()));
            }
        }
        return list;
    }

    public IPage<ProjectDTO> pageByWorkspaceId(Long workspaceId, String searchVal, int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                projectDao.selectPageByWorkspaceId(workspaceId, searchVal, pageNum, pageSize), ProjectDTO.class);
    }

    public ProjectDTO update(Long workspaceId, Long code, ProjectDTO body) {
        log.info("更新项目, workspaceId={}, code={}, name={}", workspaceId, code, body.getName());
        Project existing = getEntityByCode(workspaceId, code);
        if (projectDao.countByWorkspaceIdAndNameExcludeId(
                existing.getWorkspaceId(), body.getName(), existing.getId()) > 0) {
            throw new BizException(WorkspaceErrorCode.PROJECT_NAME_EXISTS);
        }
        Project project = BeanConvertUtils.convert(body, Project.class);
        project.setId(existing.getId()); // 锁定到现有记录
        project.setCode(existing.getCode());
        project.setWorkspaceId(existing.getWorkspaceId());
        projectDao.updateById(project);
        return BeanConvertUtils.convert(project, ProjectDTO.class);
    }

    public void updateOwner(Long workspaceId, Long code, Long newOwnerId) {
        log.info("变更项目负责人, workspaceId={}, code={}, newOwnerId={}", workspaceId, code, newOwnerId);
        Project project = getEntityByCode(workspaceId, code);
        project.setCreatedBy(newOwnerId);
        projectDao.updateById(project);
    }

    public void delete(Long workspaceId, Long code) {
        log.info("删除项目, workspaceId={}, code={}", workspaceId, code);
        Project existing = getEntityByCode(workspaceId, code);
        projectDao.deleteById(existing.getId());
    }

    /**
     * Internal method returning Entity for update/delete operations that need to modify the entity.
     */
    private Project getEntityByCode(Long workspaceId, Long code) {
        Project project = projectDao.selectByWorkspaceIdAndCode(workspaceId, code);
        if (project == null) {
            throw new NotFoundException(WorkspaceErrorCode.PROJECT_NOT_FOUND, code);
        }
        return project;
    }

    private ProjectDTO enrich(ProjectDTO dto) {
        if (dto.getCreatedBy() != null) {
            User user = userDao.selectById(dto.getCreatedBy());
            if (user != null) {
                dto.setCreatedByUsername(user.getUsername());
            }
        }
        return dto;
    }
}
