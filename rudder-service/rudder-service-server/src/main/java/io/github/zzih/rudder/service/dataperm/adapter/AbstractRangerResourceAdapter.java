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

package io.github.zzih.rudder.service.dataperm.adapter;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerAdminRestClient;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyResource;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerServiceDef;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link RangerResourceAdapter} 公共骨架。元数据来源由 {@link DataPermConfigService#active() rangerModeEnabled} 决定:
 * <ul>
 *   <li>未启用 → 走 {@code PluginType} 内置 base spec(Local-only 部署)</li>
 *   <li>启用 → 拉 Ranger Admin service-def(跨节点共享缓存 subKey = {@link #rangerServiceType()});Admin 不可达异常上抛</li>
 * </ul>
 *
 * <p>{@link #valueAt} 由子类决定 Rudder 4 元组(catalog/database/table/column)在该 plugin
 * hierarchy 下的字段映射(如 Trino 的 SCHEMA → databaseName)。
 */
abstract class AbstractRangerResourceAdapter implements RangerResourceAdapter {

    /** 资源层级缺省值占位符(Ranger 端通配语义)。 */
    private static final String WILDCARD = "*";

    // access 操作等级:READ → UPDATE → CREATE → DELETE → MANAGE → null(other) → "all" 永远最后。
    // 对应 Ranger service-def 的 accessType.category。
    private static final List<String> CATEGORY_ORDER = List.of(
            "READ", "UPDATE", "CREATE", "DELETE", "MANAGE");

    protected final GlobalCacheService cache;
    protected final RangerAdminRestClient rangerClient;
    protected final DataPermConfigService configService;

    protected AbstractRangerResourceAdapter(GlobalCacheService cache,
                                            RangerAdminRestClient rangerClient,
                                            DataPermConfigService configService) {
        this.cache = cache;
        this.rangerClient = rangerClient;
        this.configService = configService;
    }

    /** 子类钩子:从 item 取该层级的值,缺省返回 null → 替换为 {@code "*"}。 */
    protected abstract String valueAt(ResourceLevel level, DataPermPermissionItemDTO item);

    /**
     * Ranger mode 关 → 返 null,上层走 base spec;
     * Ranger mode 开 → 拉 Ranger service-def,Admin 不可达直接抛 {@link BizException}(由 RestClient 抛出)。
     */
    private RangerServiceDef fetchRangerDef() {
        if (!Boolean.TRUE.equals(configService.active().getRangerModeEnabled())) {
            return null;
        }
        return cache.getOrLoad(
                GlobalCacheKey.RANGER_SERVICE_DEFS,
                rangerServiceType(),
                RangerServiceDef.class,
                () -> sortAccessTypes(rangerClient.getServiceDef(rangerServiceType())));
    }

    /** 按操作等级重排 access types,UI 列出来跟用户思维一致;"all" 强制最后。 */
    private static RangerServiceDef sortAccessTypes(RangerServiceDef def) {
        if (def == null || def.getAccessTypes() == null) {
            return def;
        }
        List<RangerServiceDef.AccessType> sorted = def.getAccessTypes().stream()
                .sorted(Comparator
                        .comparingInt(AbstractRangerResourceAdapter::categoryRank)
                        .thenComparing(a -> a.getName() == null ? "" : a.getName()))
                .toList();
        def.setAccessTypes(sorted);
        return def;
    }

    private static int categoryRank(RangerServiceDef.AccessType at) {
        String name = at.getName();
        if ("all".equalsIgnoreCase(name)) {
            return Integer.MAX_VALUE;
        }
        String cat = at.getCategory();
        if (cat == null) {
            cat = inferCategory(name);
        }
        int idx = CATEGORY_ORDER.indexOf(cat);
        return idx < 0 ? CATEGORY_ORDER.size() : idx;
    }

    /** 部分 plugin(如 StarRocks)的 access 不带 category,按 name 关键字推断,跟 Hive/Trino 顺序对齐。 */
    private static String inferCategory(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase();
        if (lower.startsWith("create") || "alter".equals(lower)) {
            return "CREATE";
        }
        return switch (lower) {
            case "drop", "delete" -> "DELETE";
            case "insert", "update", "write", "refresh", "export" -> "UPDATE";
            case "select", "read", "show", "use", "execute", "impersonate", "usage" -> "READ";
            default -> "MANAGE";
        };
    }

    private static List<String> parseAccessTypes(RangerServiceDef def) {
        if (def == null || def.getAccessTypes() == null) {
            return List.of();
        }
        return def.getAccessTypes().stream()
                .map(RangerServiceDef.AccessType::getName)
                .filter(n -> n != null && !n.isBlank())
                .toList();
    }

    private static List<ResourceLevel> parseHierarchy(RangerServiceDef def) {
        if (def == null || def.getResources() == null) {
            return List.of();
        }
        return def.getResources().stream()
                .sorted(Comparator.comparingInt(r -> r.getLevel() == null ? Integer.MAX_VALUE : r.getLevel()))
                .map(r -> ResourceLevel.fromKey(r.getName()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Ranger def 解析非空时用 Ranger,否则走 PluginType base spec。 */
    private <T> List<T> metadataOrBase(Function<RangerServiceDef, List<T>> parse, Supplier<List<T>> base) {
        RangerServiceDef def = fetchRangerDef();
        if (def != null) {
            List<T> fromRanger = parse.apply(def);
            if (!fromRanger.isEmpty()) {
                return fromRanger;
            }
        }
        return base.get();
    }

    @Override
    public List<String> supportedAccessTypes() {
        return metadataOrBase(
                AbstractRangerResourceAdapter::parseAccessTypes,
                () -> supportedPluginType().baseAccessTypes());
    }

    @Override
    public List<ResourceLevel> resourceHierarchy() {
        return metadataOrBase(
                AbstractRangerResourceAdapter::parseHierarchy,
                () -> supportedPluginType().baseHierarchy());
    }

    @Override
    public Map<String, RangerPolicyResource> toRangerResource(DataPermPermissionItemDTO item) {
        List<ResourceLevel> levels = resourceHierarchy();
        Map<String, RangerPolicyResource> resources = new LinkedHashMap<>(levels.size() * 2);
        for (ResourceLevel level : levels) {
            String raw = valueAt(level, item);
            String value = raw == null || raw.isBlank() ? WILDCARD : raw;
            resources.put(level.rangerKey(), RangerPolicyResource.builder()
                    .values(List.of(value))
                    .isExcludes(false)
                    .isRecursive(false)
                    .build());
        }
        return resources;
    }

    @Override
    public void validateResources(DataPermPermissionItemDTO item) {
        for (ResourceLevel level : resourceHierarchy()) {
            String v = valueAt(level, item);
            if (v == null || v.isBlank()) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                        supportedPluginType() + " " + level.rangerKey() + " required");
            }
        }
    }

    @Override
    public void validateAccesses(List<String> accesses) {
        if (accesses == null || accesses.isEmpty()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    supportedPluginType() + " accesses required");
        }
        Set<String> closed = Set.copyOf(supportedAccessTypes());
        for (String a : accesses) {
            if (a == null || !closed.contains(a)) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                        supportedPluginType() + " unsupported access: " + a);
            }
        }
    }
}
