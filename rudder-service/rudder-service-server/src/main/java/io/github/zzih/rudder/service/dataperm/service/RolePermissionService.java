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

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermRolePermissionDao;
import io.github.zzih.rudder.dao.entity.DataPermRolePermission;
import io.github.zzih.rudder.dao.entity.view.DataPermRolePermissionDetailView;
import io.github.zzih.rudder.service.dataperm.adapter.RangerAdapterRegistry;
import io.github.zzih.rudder.service.dataperm.adapter.RangerResourceAdapter;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRolePermissionItemDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源包内权限项行级 CRUD。
 *
 * <p>历史还原由 Reconciler 写入的 {@code t_r_data_perm_user_effective_snapshot}(用户级事实表)提供,
 * 本类不再写 role 级 snapshot。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final DataPermRolePermissionDao permissionDao;
    private final DataPermConfigService configService;
    private final RangerAdapterRegistry adapterRegistry;

    public IPage<DataPermRolePermissionItemDTO> page(Long roleId, String keyword,
                                                     PluginType pluginType,
                                                     int pageNum, int pageSize) {
        List<Long> scopeCodes = resolveServiceCodes(pluginType);
        IPage<DataPermRolePermissionDetailView> page = permissionDao.selectPage(
                roleId, keyword, scopeCodes, pageNum, pageSize);
        return page.convert(RolePermissionService::toDto);
    }

    public List<DataPermRolePermissionItemDTO> listByRoleId(Long roleId) {
        return permissionDao.selectByRoleId(roleId).stream()
                .map(RolePermissionService::toDto)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long roleId, DataPermRolePermissionItemDTO raw) {
        DataPermRolePermissionItemDTO item = normalize(raw);
        DataPermRolePermission entity = new DataPermRolePermission();
        entity.setRoleId(roleId);
        applyToEntity(entity, item);
        permissionDao.insert(entity);
        log.info("Role permission added: roleId={}, permId={}", roleId, entity.getId());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long roleId, Long permId, DataPermRolePermissionItemDTO raw) {
        DataPermRolePermissionItemDTO item = normalize(raw);
        DataPermRolePermission entity = new DataPermRolePermission();
        entity.setId(permId);
        applyToEntity(entity, item);
        int rows = permissionDao.updateByIdAndRole(entity, roleId);
        if (rows == 0) {
            throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING,
                    "rolePermission:" + permId);
        }
        log.info("Role permission updated: roleId={}, permId={}", roleId, permId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId, Long permId) {
        int rows = permissionDao.deleteByIdAndRole(permId, roleId);
        if (rows == 0) {
            throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING,
                    "rolePermission:" + permId);
        }
        log.info("Role permission deleted: roleId={}, permId={}", roleId, permId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteByRoleId(Long roleId) {
        return permissionDao.deleteByRoleId(roleId);
    }

    private DataPermRolePermissionItemDTO normalize(DataPermRolePermissionItemDTO raw) {
        if (raw == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "item required");
        }
        if (raw.getScopeCode() == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "scopeCode required");
        }
        if (raw.getAccesses() == null || raw.getAccesses().isEmpty()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "accesses required");
        }
        List<String> sortedAccesses = raw.getAccesses().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (sortedAccesses.isEmpty()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "accesses must contain non-blank values");
        }
        // 闭集校验:按 service.pluginType 找 adapter,validateAccesses 抛 APPLICATION_INVALID
        DataPermScopeDTO svc = configService.findScope(raw.getScopeCode())
                .orElseThrow(() -> new BizException(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND,
                        raw.getScopeCode()));
        Optional<RangerResourceAdapter> adapter = adapterRegistry.find(svc.getPluginType());
        adapter.ifPresent(a -> a.validateAccesses(sortedAccesses));
        DataPermRolePermissionItemDTO normalized = DataPermRolePermissionItemDTO.builder()
                .scopeCode(raw.getScopeCode())
                .catalogName(nullIfBlank(raw.getCatalogName()))
                .databaseName(nullIfBlank(raw.getDatabaseName()))
                .tableName(nullIfBlank(raw.getTableName()))
                .columnName(nullIfBlank(raw.getColumnName()))
                .accesses(new ArrayList<>(sortedAccesses))
                .build();
        adapter.ifPresent(a -> a.validateResources(normalized));
        return normalized;
    }

    private static void applyToEntity(DataPermRolePermission entity, DataPermRolePermissionItemDTO item) {
        entity.setScopeCode(item.getScopeCode());
        entity.setCatalogName(item.getCatalogName());
        entity.setDatabaseName(item.getDatabaseName());
        entity.setTableName(item.getTableName());
        entity.setColumnName(item.getColumnName());
        entity.setAccesses(JsonUtils.toJson(item.getAccesses()));
    }

    private List<Long> resolveServiceCodes(PluginType pluginType) {
        if (pluginType == null) {
            return null;
        }
        List<Long> codes = configService.listScopes().stream()
                .filter(s -> s.getPluginType() == pluginType)
                .map(DataPermScopeDTO::getCode)
                .toList();
        return codes.isEmpty() ? Collections.emptyList() : codes;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static DataPermRolePermissionItemDTO toDto(DataPermRolePermissionDetailView e) {
        return DataPermRolePermissionItemDTO.builder()
                .id(e.getId())
                .scopeCode(e.getScopeCode())
                .scopeName(e.getScopeName())
                .catalogName(e.getCatalogName())
                .databaseName(e.getDatabaseName())
                .tableName(e.getTableName())
                .columnName(e.getColumnName())
                .accesses(JsonUtils.toList(e.getAccesses(), String.class))
                .build();
    }
}
