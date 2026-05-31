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

package io.github.zzih.rudder.service.dataperm.auth;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.sql.TableAccess;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermPlatformConfigDao;
import io.github.zzih.rudder.dao.dao.DataPermScopeDao;
import io.github.zzih.rudder.dao.dao.DataPermUserEffectiveSnapshotDao;
import io.github.zzih.rudder.dao.entity.DataPermPlatformConfig;
import io.github.zzih.rudder.dao.entity.DataPermScope;
import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker 端本地数据权限鉴权。任务执行前由实现 {@code DataPermAwareTask} 的 task 产出 access intents;
 * authorizer 用 {@code TaskType} 反查 scope (JSON_CONTAINS managed_task_types) 命中后比对 snapshot 表。
 *
 * <p>语义:
 * <ul>
 *   <li>{@code localModeEnabled=false} → 放行</li>
 *   <li>{@code taskType} 未命中任何 enabled scope → 放行(该任务类型不受管控)</li>
 *   <li>{@code userId=null} → 拒绝(系统触发任务不允许走 Local 鉴权)</li>
 *   <li>命中 scope + intent 在 snapshot 找不到匹配 grant 或 access 不足 → 拒绝</li>
 * </ul>
 *
 * <p>SQL 解析失败已在 {@link io.github.zzih.rudder.common.sql.SqlAccessResolver} 层 fail-open 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermLocalAuthorizer {

    private final GlobalCacheService cache;
    private final DataPermPlatformConfigDao platformConfigDao;
    private final DataPermScopeDao scopeDao;
    private final DataPermUserEffectiveSnapshotDao snapshotDao;

    /** 鉴权入口。{@code intents} 空或 taskType 未命中任何 scope = 放行;缺权抛 {@code LOCAL_AUTH_DENIED}。 */
    public void authorize(Long userId, TaskType taskType, List<TableAccess> intents) {
        if (intents == null || intents.isEmpty() || taskType == null) {
            return;
        }
        DataPermConfigDTO config = loadConfig();
        if (!Boolean.TRUE.equals(config.getEnabled()) || !Boolean.TRUE.equals(config.getLocalModeEnabled())) {
            return;
        }
        DataPermScope scope = scopeDao.selectByTaskType(taskType.name());
        if (scope == null) {
            return;
        }
        if (userId == null) {
            throw new BizException(DataPermErrorCode.LOCAL_AUTH_DENIED,
                    "missing submit user id for scope " + scope.getName());
        }

        PluginType pluginType = PluginType.parse(scope.getPluginType());
        Long scopeCode = scope.getCode();
        List<DataPermUserEffectiveSnapshot> grants = snapshotDao.selectLatest(userId).stream()
                .filter(g -> g.getScopeCode() != null)
                .filter(g -> scopeCode.equals(g.getScopeCode()))
                .toList();

        // grant 端任一行有具体 catalog/database (非 * 非 null) 时,intent 必须也提供,
        // 否则无法判断访问目标到底落在哪个 catalog/database;不强制全限定会等于把鉴权放宽到通配。
        boolean grantHasCatalog = false;
        boolean grantHasDatabase = false;
        for (DataPermUserEffectiveSnapshot g : grants) {
            if (!grantHasCatalog && isConcrete(g.getCatalogName())) {
                grantHasCatalog = true;
            }
            if (!grantHasDatabase && isConcrete(g.getDatabaseName())) {
                grantHasDatabase = true;
            }
            if (grantHasCatalog && grantHasDatabase) {
                break;
            }
        }

        List<String> unqualified = new ArrayList<>();
        List<String> denials = new ArrayList<>();
        for (TableAccess intent : intents) {
            String missing = missingQualifier(intent, grantHasCatalog, grantHasDatabase);
            if (missing != null) {
                unqualified.add(intent.table() + " (缺少 " + missing + ")");
                continue;
            }
            // 该 plugin 无法表达此动作(如 Trino 无 update)或 pluginType 解析不出 → needAccess 为 null → fail-closed 拒。
            String needAccess = pluginType == null ? null : pluginType.accessFor(intent.action());
            if (!isAllowed(needAccess, intent, grants)) {
                denials.add(formatDenial(needAccess, scope, intent));
            }
        }
        if (!unqualified.isEmpty()) {
            throw new BizException(DataPermErrorCode.LOCAL_AUTH_UNQUALIFIED_TABLE,
                    String.join("; ", unqualified));
        }
        if (!denials.isEmpty()) {
            throw new BizException(DataPermErrorCode.LOCAL_AUTH_DENIED, String.join("; ", denials));
        }
    }

    private static boolean isConcrete(String v) {
        return v != null && !v.isEmpty() && !"*".equals(v);
    }

    /** intent 缺哪段限定;不缺返 null。 */
    private static String missingQualifier(TableAccess intent, boolean needCatalog, boolean needDatabase) {
        boolean missCat = needCatalog && (intent.catalog() == null || intent.catalog().isEmpty());
        boolean missDb = needDatabase && (intent.database() == null || intent.database().isEmpty());
        if (!missCat && !missDb) {
            return null;
        }
        if (missCat && missDb) {
            return "catalog 和 database";
        }
        return missCat ? "catalog" : "database";
    }

    /** 主配置走 cache(每 task 鉴权都查 enabled/localModeEnabled);DB 空时返一个 disabled 占位 DTO 走放行。 */
    private DataPermConfigDTO loadConfig() {
        return cache.getOrLoad(GlobalCacheKey.DATA_PERM, () -> {
            DataPermPlatformConfig entity = platformConfigDao.selectActive();
            DataPermConfigDTO dto = new DataPermConfigDTO();
            if (entity == null) {
                dto.setEnabled(false);
                dto.setLocalModeEnabled(false);
                dto.setRangerModeEnabled(false);
                return dto;
            }
            BeanUtils.copyProperties(entity, dto);
            return dto;
        });
    }

    private static boolean isAllowed(String needAccess, TableAccess intent,
                                     List<DataPermUserEffectiveSnapshot> grants) {
        // needAccess 为 null = 该 plugin 表达不了此动作 → fail-closed 拒(调用方已统一计算)。
        if (needAccess == null) {
            return false;
        }
        // 先收集 (table 匹配 + access 含) 的候选 grant 集合,再做列覆盖判定。
        // 单 grant 不一定覆盖所有列,但多个 grant 合并可能覆盖 → 必须聚合判定而非任一返回。
        List<DataPermUserEffectiveSnapshot> covering = new java.util.ArrayList<>();
        for (DataPermUserEffectiveSnapshot g : grants) {
            if (matchResource(intent, g) && containsAccess(g.getAccesses(), needAccess)) {
                covering.add(g);
            }
        }
        if (covering.isEmpty()) {
            return false;
        }
        // intent.columns 空 → 表级访问 = 任一 grant 覆盖整表(grant.columnName 为 null 或 '*')
        if (intent.columns() == null || intent.columns().isEmpty()) {
            for (DataPermUserEffectiveSnapshot g : covering) {
                if (isWholeTable(g.getColumnName())) {
                    return true;
                }
            }
            return false;
        }
        // intent.columns 非空 → 每个引用列必须被至少一个 grant 覆盖
        for (String col : intent.columns()) {
            boolean covered = false;
            for (DataPermUserEffectiveSnapshot g : covering) {
                if (isWholeTable(g.getColumnName()) || equalsIgnoreCase(g.getColumnName(), col)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchResource(TableAccess intent, DataPermUserEffectiveSnapshot grant) {
        return matchLevel(intent.catalog(), grant.getCatalogName())
                && matchLevel(intent.database(), grant.getDatabaseName())
                && matchLevel(intent.table(), grant.getTableName());
    }

    /** grant.columnName 非具体值(null/空/"*")时视为整表覆盖,与 catalog/database 通配语义一致。 */
    private static boolean isWholeTable(String grantColumn) {
        return !isConcrete(grantColumn);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    /** grant 端 {@code "*"} = 通配;{@code null} 表示该 plugin 不适用此层(两层引擎无 catalog),intent 也应为 null。 */
    private static boolean matchLevel(String intent, String grant) {
        if (grant == null) {
            return intent == null;
        }
        if ("*".equals(grant)) {
            return true;
        }
        return intent != null && intent.equalsIgnoreCase(grant);
    }

    private static boolean containsAccess(String accessesJson, String needAccess) {
        if (accessesJson == null || accessesJson.isBlank()) {
            return false;
        }
        List<String> accesses = JsonUtils.toList(accessesJson, String.class);
        if (accesses == null) {
            return false;
        }
        // grant 含具体 access 即覆盖;含 "all"(plugin 通配 access)覆盖一切,语义与 Ranger 对齐。
        return accesses.stream()
                .anyMatch(a -> a != null && (a.equalsIgnoreCase(needAccess) || "all".equalsIgnoreCase(a)));
    }

    private static String formatDenial(String needAccess, DataPermScope scope, TableAccess intent) {
        String path = java.util.stream.Stream.of(intent.catalog(), intent.database(), intent.table())
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("."));
        String cols = intent.columns() == null || intent.columns().isEmpty()
                ? ""
                : "[" + String.join(",", intent.columns()) + "]";
        return scope.getName() + "." + path + cols + " " + (needAccess != null ? needAccess : intent.action().name());
    }
}
