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

package io.github.zzih.rudder.service.dataperm.config;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.DataPermBundleStatementDao;
import io.github.zzih.rudder.dao.dao.DataPermPlatformConfigDao;
import io.github.zzih.rudder.dao.dao.DataPermScopeAccessGroupDao;
import io.github.zzih.rudder.dao.dao.DataPermScopeDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.dao.DatasourceDao;
import io.github.zzih.rudder.dao.entity.DataPermPlatformConfig;
import io.github.zzih.rudder.dao.entity.DataPermScope;
import io.github.zzih.rudder.dao.entity.DataPermScopeAccessGroup;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeAccessGroupDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.service.DataPermScopeAccessGroupService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据权限平台配置 + scope 管理。
 *
 * <p>主配置 {@link #active()} 走 cache(每 task 鉴权前都查 enabled / localModeEnabled);
 * scope 增删改查不缓存,直接走 {@link DataPermScopeDao},每次按需查 DB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermConfigService {

    private final GlobalCacheService cache;
    private final DataPermPlatformConfigDao dao;
    private final DataPermScopeDao scopeDao;
    private final DatasourceDao datasourceDao;
    private final DataPermBundleStatementDao bundleStatementDao;
    private final DataPermUserDirectGrantDao directGrantDao;
    private final DataPermScopeAccessGroupDao accessGroupDao;

    public DataPermConfigDTO active() {
        return cache.getOrLoad(GlobalCacheKey.DATA_PERM, this::build);
    }

    public HealthStatus health() {
        return HealthStatus.healthy();
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(active().getEnabled());
    }

    public List<DataPermScopeDTO> listScopes() {
        return scopeDao.selectAll().stream().map(DataPermConfigService::toDto).toList();
    }

    /** 配置编辑用:每个 scope 带上其操作分组(含 accesses)。一次全量查分组,内存按 scopeCode 分组避免 N+1。 */
    public List<DataPermScopeDTO> listScopesWithGroups() {
        Map<Long, List<DataPermScopeAccessGroupDTO>> groupsByScope = accessGroupDao.selectAll().stream()
                .map(DataPermScopeAccessGroupService::toDto)
                .collect(Collectors.groupingBy(DataPermScopeAccessGroupDTO::getScopeCode));
        return scopeDao.selectAll().stream().map(row -> {
            DataPermScopeDTO dto = toDto(row);
            dto.setAccessGroups(groupsByScope.getOrDefault(row.getCode(), List.of()));
            return dto;
        }).toList();
    }

    public Optional<DataPermScopeDTO> findScope(Long code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scopeDao.selectByCode(code)).map(DataPermConfigService::toDto);
    }

    public Optional<DataPermScopeDTO> findScopeByTaskType(TaskType taskType) {
        if (taskType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scopeDao.selectByTaskType(taskType.name()))
                .map(DataPermConfigService::toDto);
    }

    /**
     * 保存平台配置 + 全量替换 scope 列表(原子事务)。
     * <ul>
     *   <li>新 scope(code=null)分配 {@code CodeGenerateUtils.genCode()}</li>
     *   <li>同名 scope 重复 → {@code RANGER_SERVICE_NAME_DUPLICATE}</li>
     *   <li>metadataDatasourceId 必填且 datasource 存在 → {@code RANGER_SERVICE_METADATA_DS_MISSING}</li>
     *   <li>incoming 不含的旧 scope 仍被 permission item / direct grant 引用 → {@code RANGER_SERVICE_IN_USE}</li>
     *   <li>managedTaskTypes 跨 scope 全局唯一 → {@code MANAGED_TASK_TYPE_DUPLICATE}</li>
     * </ul>
     * incomingScopes 为 null 表示不动 scope 列表(只改标量)。
     */
    @Transactional
    public void saveDetail(DataPermConfigDTO settings, List<DataPermScopeDTO> incomingScopes) {
        // 校验用 incomingScopes 决定 localMode 是否有受管 TaskType,避免读 DB 旧值跟新值错位。
        List<DataPermScopeDTO> normalized = incomingScopes == null
                ? List.of()
                : normalizeScopes(incomingScopes, Boolean.TRUE.equals(settings.getRangerModeEnabled()));
        validateModes(settings, incomingScopes == null ? null : normalized);

        if (incomingScopes != null) {
            replaceScopes(normalized);
        }

        DataPermPlatformConfig main = Optional.ofNullable(dao.selectActive())
                .orElseGet(DataPermPlatformConfig::new);
        BeanUtils.copyProperties(settings, main);
        if (main.getId() == null) {
            dao.insert(main);
        } else {
            dao.updateById(main);
        }
        cache.invalidate(GlobalCacheKey.DATA_PERM);
        // Ranger Admin URL/credential 可能改了,顺手清掉 service-def 缓存
        cache.invalidate(GlobalCacheKey.RANGER_SERVICE_DEFS);
    }

    private void replaceScopes(List<DataPermScopeDTO> normalized) {
        Map<Long, DataPermScope> existing = scopeDao.selectAll().stream()
                .collect(Collectors.toMap(DataPermScope::getCode, e -> e));
        Set<Long> incomingCodes = normalized.stream().map(DataPermScopeDTO::getCode).collect(Collectors.toSet());

        for (DataPermScope old : existing.values()) {
            if (!incomingCodes.contains(old.getCode())) {
                requireNotInUse(old.getCode(), old.getName());
                accessGroupDao.deleteByScopeCode(old.getCode());
                scopeDao.deleteByCode(old.getCode());
            }
        }
        for (DataPermScopeDTO dto : normalized) {
            DataPermScope row = existing.get(dto.getCode());
            if (row == null) {
                row = new DataPermScope();
            }
            applyDto(dto, row);
            if (row.getId() == null) {
                scopeDao.insert(row);
            } else {
                scopeDao.updateById(row);
            }
            persistGroups(dto.getCode(), dto.getAccessGroups());
        }
    }

    /**
     * 全量替换某 scope 下的操作分组。incoming 为 null 表示不动;非 null 时:有 id 的更新、无 id 的新增、
     * 缺席的删除(仍被权限项 / 授权引用则抛 {@code ACCESS_GROUP_IN_USE})。
     */
    private void persistGroups(Long scopeCode, List<DataPermScopeAccessGroupDTO> incoming) {
        if (incoming == null) {
            return;
        }
        Set<String> seenNames = new HashSet<>();
        for (DataPermScopeAccessGroupDTO g : incoming) {
            if (g.getName() == null || g.getName().isBlank()) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "accessGroup.name required");
            }
            if (g.getAccesses() == null || g.getAccesses().isEmpty()) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "accessGroup.accesses required");
            }
            if (!seenNames.add(g.getName().trim().toLowerCase())) {
                throw new BizException(DataPermErrorCode.ACCESS_GROUP_NAME_DUPLICATE, g.getName());
            }
        }
        Set<Long> existingIds = accessGroupDao.selectByScopeCode(scopeCode).stream()
                .map(DataPermScopeAccessGroup::getId)
                .collect(Collectors.toSet());
        // 带 id 的必须是本 scope 现有分组,否则是越权改归属(把别的 scope 的分组挪过来)。
        for (DataPermScopeAccessGroupDTO g : incoming) {
            if (g.getId() != null && !existingIds.contains(g.getId())) {
                throw new BizException(DataPermErrorCode.ACCESS_GROUP_SCOPE_MISMATCH, g.getId());
            }
        }
        Set<Long> incomingIds = incoming.stream()
                .map(DataPermScopeAccessGroupDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long oldId : existingIds) {
            if (!incomingIds.contains(oldId)) {
                long refs = bundleStatementDao.countByGroupId(oldId)
                        + directGrantDao.countByGroupId(oldId);
                if (refs > 0) {
                    throw new BizException(DataPermErrorCode.ACCESS_GROUP_IN_USE, refs);
                }
                accessGroupDao.deleteById(oldId);
            }
        }
        for (DataPermScopeAccessGroupDTO g : incoming) {
            DataPermScopeAccessGroup row = new DataPermScopeAccessGroup();
            row.setScopeCode(scopeCode);
            row.setName(g.getName().trim());
            row.setAccesses(JsonUtils.toJson(g.getAccesses()));
            row.setDescription(g.getDescription());
            if (g.getId() == null) {
                accessGroupDao.insert(row);
            } else {
                row.setId(g.getId());
                // 显式 update 而非 updateById:后者跳过 null 字段,清空 description 会落不进库。
                accessGroupDao.update(row);
            }
        }
    }

    /** {@code incomingScopes} 为 null 时表示 scope 不变,从 DB 读判断 localMode 是否有 managed TaskType。 */
    private void validateModes(DataPermConfigDTO settings, List<DataPermScopeDTO> incomingScopes) {
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return;
        }
        if (!Boolean.TRUE.equals(settings.getRangerModeEnabled())
                && !Boolean.TRUE.equals(settings.getLocalModeEnabled())) {
            throw new BizException(DataPermErrorCode.AT_LEAST_ONE_MODE_REQUIRED);
        }
        if (Boolean.TRUE.equals(settings.getRangerModeEnabled())
                && (settings.getRangerAdminUrl() == null || settings.getRangerAdminUrl().isBlank())) {
            throw new BizException(DataPermErrorCode.RANGER_ADMIN_URL_REQUIRED);
        }
        if (Boolean.TRUE.equals(settings.getLocalModeEnabled())) {
            boolean anyManaged = incomingScopes != null
                    ? incomingScopes.stream()
                            .anyMatch(s -> Boolean.TRUE.equals(s.getEnabled())
                                    && s.getManagedTaskTypes() != null
                                    && !s.getManagedTaskTypes().isEmpty())
                    : scopeDao.selectAll().stream()
                            .anyMatch(s -> Boolean.TRUE.equals(s.getEnabled())
                                    && s.getManagedTaskTypes() != null
                                    && !s.getManagedTaskTypes().isBlank()
                                    && !"[]".equals(s.getManagedTaskTypes()));
            if (!anyManaged) {
                throw new BizException(DataPermErrorCode.LOCAL_MODE_NO_MANAGED_TASK_TYPE);
            }
        }
    }

    private List<DataPermScopeDTO> normalizeScopes(List<DataPermScopeDTO> incoming, boolean rangerModeEnabled) {
        if (incoming == null) {
            incoming = List.of();
        }
        Set<String> seenNames = new HashSet<>();
        Set<TaskType> seenTaskTypes = new HashSet<>();
        List<DataPermScopeDTO> result = new ArrayList<>(incoming.size());
        for (DataPermScopeDTO s : incoming) {
            if (s.getName() == null || s.getName().isBlank()) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "dataPermScope.name required");
            }
            if (s.getPluginType() == null) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "dataPermScope.pluginType required");
            }
            if (!seenNames.add(s.getName())) {
                throw new BizException(DataPermErrorCode.RANGER_SERVICE_NAME_DUPLICATE, s.getName());
            }
            if (s.getMetadataDatasourceId() == null
                    || datasourceDao.selectById(s.getMetadataDatasourceId()) == null) {
                throw new BizException(DataPermErrorCode.RANGER_SERVICE_METADATA_DS_MISSING, s.getName());
            }
            if (rangerModeEnabled
                    && (s.getRangerServiceName() == null || s.getRangerServiceName().isBlank())) {
                throw new BizException(DataPermErrorCode.RANGER_SERVICE_NAME_REQUIRED, s.getName());
            }
            List<TaskType> taskTypes = s.getManagedTaskTypes() == null ? List.of() : s.getManagedTaskTypes();
            for (TaskType taskType : taskTypes) {
                if (!seenTaskTypes.add(taskType)) {
                    throw new BizException(DataPermErrorCode.MANAGED_TASK_TYPE_DUPLICATE, taskType.name());
                }
            }
            DataPermScopeDTO normalized = new DataPermScopeDTO();
            normalized.setCode(s.getCode() != null ? s.getCode() : CodeGenerateUtils.genCode());
            normalized.setName(s.getName());
            normalized.setPluginType(s.getPluginType());
            normalized.setMetadataDatasourceId(s.getMetadataDatasourceId());
            normalized.setManagedTaskTypes(taskTypes);
            normalized.setRangerServiceName(s.getRangerServiceName());
            normalized.setDescription(s.getDescription());
            normalized.setEnabled(s.getEnabled() == null ? Boolean.TRUE : s.getEnabled());
            normalized.setAccessGroups(s.getAccessGroups());
            result.add(normalized);
        }
        return result;
    }

    private void requireNotInUse(Long code, String name) {
        if (code == null) {
            return;
        }
        if (bundleStatementDao.countByScopeCode(code) > 0
                || directGrantDao.countByScopeCode(code) > 0) {
            throw new BizException(DataPermErrorCode.RANGER_SERVICE_IN_USE, name);
        }
    }

    private DataPermConfigDTO build() {
        DataPermPlatformConfig main = dao.selectActive();
        if (main == null) {
            return defaultsDto();
        }
        DataPermConfigDTO dto = new DataPermConfigDTO();
        BeanUtils.copyProperties(main, dto);
        return dto;
    }

    private static DataPermConfigDTO defaultsDto() {
        DataPermConfigDTO d = new DataPermConfigDTO();
        d.setEnabled(false);
        d.setRangerModeEnabled(false);
        d.setLocalModeEnabled(false);
        d.setRangerAdminTimeoutMs(10_000);
        d.setRangerAdminPageSize(1_000);
        d.setRangerWriteConcurrency(4);
        d.setReconcileIntervalSeconds(300);
        d.setReconcileLockTtlSeconds(600);
        d.setReconcileBatchSize(100);
        d.setReconcileFailureAlertThreshold(3);
        d.setEnsureRangerUser(false);
        return d;
    }

    static DataPermScopeDTO toDto(DataPermScope row) {
        DataPermScopeDTO dto = new DataPermScopeDTO();
        dto.setCode(row.getCode());
        dto.setName(row.getName());
        dto.setPluginType(PluginType.parse(row.getPluginType()));
        dto.setMetadataDatasourceId(row.getMetadataDatasourceId());
        dto.setManagedTaskTypes(row.getManagedTaskTypes() == null || row.getManagedTaskTypes().isBlank()
                ? List.of()
                : JsonUtils.toList(row.getManagedTaskTypes(), TaskType.class));
        dto.setRangerServiceName(row.getRangerServiceName());
        dto.setDescription(row.getDescription());
        dto.setEnabled(row.getEnabled());
        return dto;
    }

    private static void applyDto(DataPermScopeDTO dto, DataPermScope row) {
        row.setCode(dto.getCode());
        row.setName(dto.getName());
        row.setPluginType(dto.getPluginType() == null ? null : dto.getPluginType().name());
        row.setMetadataDatasourceId(dto.getMetadataDatasourceId());
        row.setManagedTaskTypes(JsonUtils.toJson(
                dto.getManagedTaskTypes() == null ? List.of() : dto.getManagedTaskTypes()));
        row.setRangerServiceName(dto.getRangerServiceName());
        row.setDescription(dto.getDescription());
        row.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
    }
}
