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
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.DataPermRoleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserRoleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermRole;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRoleDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源包(role)元信息 CRUD;权限项明细维护见 {@link RolePermissionService}。
 *
 * <p>{@link #delete} 会级联失效所有引用 grants → 删主表 permission → 删 role。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final DataPermRoleDao roleDao;
    private final RolePermissionService rolePermissionService;
    private final DataPermUserRoleGrantDao userRoleGrantDao;

    /** 分页查询 + name/description 模糊搜索。 */
    public com.baomidou.mybatisplus.core.metadata.IPage<DataPermRoleDTO> listPage(
                                                                                  String keyword, int pageNum,
                                                                                  int pageSize) {
        com.baomidou.mybatisplus.core.metadata.IPage<DataPermRole> page =
                roleDao.selectPage(keyword, pageNum, pageSize);
        Map<Long, Long> countByRole = userRoleGrantDao.countActiveByRoleIds(
                page.getRecords().stream().map(DataPermRole::getId).toList(), LocalDateTime.now());
        return page.convert(r -> toBriefDto(r, countByRole.getOrDefault(r.getId(), 0L)));
    }

    /** 列出所有 role,附当前授权独立用户数(无权限项明细)。 */
    public List<DataPermRoleDTO> listAll() {
        List<DataPermRole> roles = roleDao.selectAll();
        Map<Long, Long> countByRole = userRoleGrantDao.countActiveByRoleIds(
                roles.stream().map(DataPermRole::getId).toList(), LocalDateTime.now());
        return roles.stream()
                .map(r -> toBriefDto(r, countByRole.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    private DataPermRoleDTO toBriefDto(DataPermRole r, long activeGrantCount) {
        return DataPermRoleDTO.builder()
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
    public DataPermRoleDTO get(Long roleId) {
        DataPermRole role = requireRole(roleId);
        long activeCount = userRoleGrantDao.countActiveByRoleId(roleId, LocalDateTime.now());
        return DataPermRoleDTO.builder()
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
        DataPermRole role = new DataPermRole();
        role.setName(name);
        role.setDescription(description);
        roleDao.insert(role);
        log.info("Data perm role created: id={}, name={}", role.getId(), name);
        return role.getId();
    }

    /** 改资源包元信息(name / description)。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateMeta(Long roleId, String name, String description) {
        DataPermRole role = requireRole(roleId);
        validateName(name, roleId);
        role.setName(name);
        role.setDescription(description);
        roleDao.updateById(role);
        log.info("Data perm role meta updated: id={}, name={}", roleId, name);
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
    public int delete(Long roleId) {
        DataPermRole role = requireRole(roleId);
        int affectedGrants = userRoleGrantDao.expireAllByRoleId(
                roleId, LocalDateTime.now(), DataPermGrantEndReason.ROLE_DELETED.name());
        rolePermissionService.deleteByRoleId(roleId);
        roleDao.deleteById(roleId);
        log.info("Data perm role deleted: id={}, name={}, cascadeExpiredGrants={}",
                roleId, role.getName(), affectedGrants);
        return affectedGrants;
    }

    /** 仅查 role 实体(常用断言),不存在抛 NotFoundException。 */
    private DataPermRole requireRole(Long roleId) {
        DataPermRole role = roleDao.selectById(roleId);
        if (role == null) {
            throw new NotFoundException(DataPermErrorCode.ROLE_NOT_FOUND, roleId);
        }
        return role;
    }

    private void validateName(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "role name");
        }
        long count = excludeId == null
                ? roleDao.countByName(name)
                : roleDao.countByNameExcludeId(name, excludeId);
        if (count > 0) {
            throw new BizException(DataPermErrorCode.ROLE_NAME_DUPLICATE, name);
        }
    }

}
