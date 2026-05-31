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

package io.github.zzih.rudder.service.dataperm.service;

import io.github.zzih.rudder.common.enums.dataperm.DataPermGrantEndReason;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.enums.workspace.WorkspaceResourceType;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.service.dataperm.dto.DataPermBundleDTO;
import io.github.zzih.rudder.service.permission.WorkspacePermissionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源包(role)元信息 CRUD;权限项明细维护见 {@link BundleStatementService}。
 *
 * <p>{@link #delete} 会级联失效所有引用 grants → 删主表 permission → 删 role。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleService {

    private final DataPermBundleDao bundleDao;
    private final BundleStatementService bundleStatementService;
    private final DataPermUserBundleGrantDao userBundleGrantDao;
    private final WorkspacePermissionService workspacePermissionService;

    /** 分页查询 + name/description 模糊搜索。 */
    public com.baomidou.mybatisplus.core.metadata.IPage<DataPermBundleDTO> listPage(
                                                                                    String keyword, int pageNum,
                                                                                    int pageSize) {
        com.baomidou.mybatisplus.core.metadata.IPage<DataPermBundle> page =
                bundleDao.selectPage(keyword, pageNum, pageSize);
        Map<Long, Long> countByRole = userBundleGrantDao.countActiveByBundleIds(
                page.getRecords().stream().map(DataPermBundle::getId).toList(), LocalDateTime.now());
        return page.convert(r -> toBriefDto(r, countByRole.getOrDefault(r.getId(), 0L)));
    }

    /** 申请侧:列出当前工作空间可见的资源包(可见性见 {@code t_r_workspace_permission})。 */
    public List<DataPermBundleDTO> listVisibleToWorkspace(Long workspaceId) {
        List<Long> bundleIds = workspacePermissionService.listResourceIdsByWorkspace(
                WorkspaceResourceType.DATA_PERM_BUNDLE, workspaceId);
        if (bundleIds.isEmpty()) {
            return List.of();
        }
        List<DataPermBundle> bundles = bundleDao.selectByIds(bundleIds);
        Map<Long, Long> countByRole = userBundleGrantDao.countActiveByBundleIds(
                bundles.stream().map(DataPermBundle::getId).toList(), LocalDateTime.now());
        return bundles.stream()
                .map(r -> toBriefDto(r, countByRole.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    private DataPermBundleDTO toBriefDto(DataPermBundle r, long activeGrantCount) {
        return DataPermBundleDTO.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy())
                .updatedBy(r.getUpdatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .activeGrantCount(activeGrantCount)
                .build();
    }

    /** 取指定 role 元信息 + 当前授权独立用户数;权限项明细走分页 endpoint。 */
    public DataPermBundleDTO get(Long bundleId) {
        DataPermBundle role = requireRole(bundleId);
        long activeCount = userBundleGrantDao.countActiveByBundleId(bundleId, LocalDateTime.now());
        return DataPermBundleDTO.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .createdBy(role.getCreatedBy())
                .updatedBy(role.getUpdatedBy())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .activeGrantCount(activeCount)
                .build();
    }

    /**
     * 创建资源包(仅元信息)。权限项创建后由独立 endpoint 行级新增。
     * 名称重复抛 {@link DataPermErrorCode#ROLE_NAME_DUPLICATE}。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(String name, String description) {
        validateName(name, null);
        DataPermBundle role = new DataPermBundle();
        role.setName(name);
        role.setDescription(description);
        bundleDao.insert(role);
        log.info("Data perm role created: id={}, name={}", role.getId(), name);
        return role.getId();
    }

    /** 改资源包元信息(name / description)。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateMeta(Long bundleId, String name, String description) {
        DataPermBundle role = requireRole(bundleId);
        validateName(name, bundleId);
        role.setName(name);
        role.setDescription(description);
        bundleDao.updateById(role);
        log.info("Data perm role meta updated: id={}, name={}", bundleId, name);
    }

    /**
     * 硬删资源包 + 级联失效所有引用 grants(snapshot 保留)。
     * <ul>
     *   <li>UPDATE 所有 active {@code _user_role_grant} → {@code expiration_time=NOW, end_reason=ROLE_DELETED}</li>
     *   <li>DELETE {@code _role_permission}(snapshot 不删)</li>
     *   <li>DELETE {@code _role}</li>
     * </ul>
     * 返回级联失效的 grant 行数(audit / UI 用)。
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long bundleId) {
        DataPermBundle role = requireRole(bundleId);
        int affectedGrants = userBundleGrantDao.expireAllByBundleId(
                bundleId, LocalDateTime.now(), DataPermGrantEndReason.ROLE_DELETED.name());
        bundleStatementService.deleteByBundleId(bundleId);
        workspacePermissionService.revokeAll(WorkspaceResourceType.DATA_PERM_BUNDLE, bundleId);
        bundleDao.deleteById(bundleId);
        log.info("Data perm role deleted: id={}, name={}, cascadeExpiredGrants={}",
                bundleId, role.getName(), affectedGrants);
        return affectedGrants;
    }

    /** 仅查 role 实体(常用断言),不存在抛 NotFoundException。 */
    private DataPermBundle requireRole(Long bundleId) {
        DataPermBundle role = bundleDao.selectById(bundleId);
        if (role == null) {
            throw new NotFoundException(DataPermErrorCode.ROLE_NOT_FOUND, bundleId);
        }
        return role;
    }

    private void validateName(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "role name");
        }
        long count = excludeId == null
                ? bundleDao.countByName(name)
                : bundleDao.countByNameExcludeId(name, excludeId);
        if (count > 0) {
            throw new BizException(DataPermErrorCode.ROLE_NAME_DUPLICATE, name);
        }
    }

}
