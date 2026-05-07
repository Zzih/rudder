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

package io.github.zzih.rudder.mcp.capability;

import static io.github.zzih.rudder.common.enums.auth.RoleType.DEVELOPER;
import static io.github.zzih.rudder.common.enums.auth.RoleType.SUPER_ADMIN;
import static io.github.zzih.rudder.common.enums.auth.RoleType.VIEWER;
import static io.github.zzih.rudder.common.enums.auth.RoleType.WORKSPACE_OWNER;
import static io.github.zzih.rudder.mcp.capability.RwClass.READ;
import static io.github.zzih.rudder.mcp.capability.RwClass.WRITE;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.mcp.capability.Capability.Sensitivity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP Capability 矩阵 — 单一真理来源。
 *
 * <p>对应 docs/mcp-platform.md §3.3.2 角色 → 可操作能力矩阵。
 * 4 角色 × 20 capability，覆盖 9 个域：workspace / metadata / datasource /
 * project / script / execution / workflow / approval / knowledge。
 *
 * <p>每个 capability 声明 {@code requiredRoles} —— 该角色集合内任一角色才能拥有此 capability。
 * 矩阵在编译期固定，运行时通过 {@code CapabilityRegistry} 暴露给 ScopeChecker / 前端 scope 列表。
 *
 * <p>新增 capability：在此处加 entry + 写对应 Tool 实现，单测会强制断言矩阵完整性。
 */
public final class CapabilityCatalog {

    /**
     * 完整的 20 个 capability 定义（按域分组，对应 §3.3.2 表格行序）。
     */
    public static final List<Capability> ALL = List.of(
            // workspace
            new Capability(CapabilityIds.WORKSPACE_VIEW, "workspace", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.WORKSPACE_VIEW),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // metadata
            new Capability(CapabilityIds.METADATA_BROWSE, "metadata", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.METADATA_BROWSE),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // datasource
            new Capability(CapabilityIds.DATASOURCE_VIEW, "datasource", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.DATASOURCE_VIEW),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.DATASOURCE_TEST, "datasource", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.DATASOURCE_TEST),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // datasource.manage 全局资源，二级审批兜底（虽 RBAC 已限 SUPER_ADMIN，仍走 owner→admin 留痕）
            new Capability(CapabilityIds.DATASOURCE_MANAGE, "datasource", WRITE, Sensitivity.HIGH,
                    descKey(CapabilityIds.DATASOURCE_MANAGE),
                    Set.of(SUPER_ADMIN)),
            // project
            new Capability(CapabilityIds.PROJECT_BROWSE, "project", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.PROJECT_BROWSE),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.PROJECT_AUTHOR, "project", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.PROJECT_AUTHOR),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // script
            new Capability(CapabilityIds.SCRIPT_BROWSE, "script", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.SCRIPT_BROWSE),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.SCRIPT_AUTHOR, "script", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.SCRIPT_AUTHOR),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // execution
            new Capability(CapabilityIds.EXECUTION_VIEW_STATUS, "execution", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.EXECUTION_VIEW_STATUS),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.EXECUTION_VIEW_RESULT, "execution", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.EXECUTION_VIEW_RESULT),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // execution.run：生产环境跑 SQL 有副作用风险，二级审批
            new Capability(CapabilityIds.EXECUTION_RUN, "execution", WRITE, Sensitivity.HIGH,
                    descKey(CapabilityIds.EXECUTION_RUN),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.EXECUTION_CANCEL, "execution", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.EXECUTION_CANCEL),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // workflow
            new Capability(CapabilityIds.WORKFLOW_BROWSE, "workflow", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.WORKFLOW_BROWSE),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.WORKFLOW_AUTHOR, "workflow", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.WORKFLOW_AUTHOR),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.WORKFLOW_RUN, "workflow", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.WORKFLOW_RUN),
                    Set.of(DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            // workflow.publish：发布到生产是平台级影响，二级审批
            new Capability(CapabilityIds.WORKFLOW_PUBLISH, "workflow", WRITE, Sensitivity.HIGH,
                    descKey(CapabilityIds.WORKFLOW_PUBLISH),
                    Set.of(WORKSPACE_OWNER, SUPER_ADMIN)),
            // approval
            new Capability(CapabilityIds.APPROVAL_VIEW, "approval", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.APPROVAL_VIEW),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)),
            new Capability(CapabilityIds.APPROVAL_ACT, "approval", WRITE, Sensitivity.NORMAL,
                    descKey(CapabilityIds.APPROVAL_ACT),
                    Set.of(WORKSPACE_OWNER, SUPER_ADMIN)),
            // knowledge base (RAG search) — 全角色可用，命中范围已按 workspace 严格过滤
            new Capability(CapabilityIds.KNOWLEDGE_SEARCH, "knowledge", READ, Sensitivity.NORMAL,
                    descKey(CapabilityIds.KNOWLEDGE_SEARCH),
                    Set.of(VIEWER, DEVELOPER, WORKSPACE_OWNER, SUPER_ADMIN)));

    /** Capability i18n description key,统一从 id 派生:{@code capability.<id>.description}。 */
    private static String descKey(String capabilityId) {
        return "capability." + capabilityId + ".description";
    }

    private static final Map<String, Capability> BY_ID = buildIndex();

    /** 20 项 DTO 按 locale lang 缓存(zh / en),消除 controller per-request 20× I18n.t 调用。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, List<CapabilityDTO>> DTO_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Map<String, Capability> buildIndex() {
        Map<String, Capability> map = new LinkedHashMap<>(ALL.size() * 2);
        for (Capability c : ALL) {
            if (map.put(c.id(), c) != null) {
                throw new IllegalStateException("Duplicate capability id: " + c.id());
            }
        }
        return Map.copyOf(map);
    }

    private CapabilityCatalog() {
    }

    public static Capability requireById(String id) {
        Capability c = BY_ID.get(id);
        if (c == null) {
            throw new IllegalArgumentException("Unknown capability: " + id);
        }
        return c;
    }

    public static Capability findById(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Capability> all() {
        return ALL;
    }

    /** 当前角色可申请的全部 capability 列表（前端 token 创建对话框用）。 */
    public static List<Capability> availableFor(RoleType role) {
        if (role == null) {
            return List.of();
        }
        return ALL.stream().filter(c -> c.isAllowedFor(role)).toList();
    }

    /** 全量 DTO,description 按当前线程 locale 解析(经 {@link I18n#t(String, Object...)})。 */
    public static List<CapabilityDTO> allDTO() {
        return allDTO(null);
    }

    /** 全量 DTO,description 按指定 locale 解析。 */
    public static List<CapabilityDTO> allDTO(Locale locale) {
        return cachedDTOs(locale);
    }

    /** 当前角色可申请的 capability DTO 列表(线程 locale)。 */
    public static List<CapabilityDTO> availableForDTO(RoleType role) {
        return availableForDTO(role, null);
    }

    /** 当前角色可申请的 capability DTO 列表(指定 locale)。 */
    public static List<CapabilityDTO> availableForDTO(RoleType role, Locale locale) {
        if (role == null) {
            return List.of();
        }
        String roleName = role.name();
        return cachedDTOs(locale).stream()
                .filter(d -> d.getRequiredRoles().contains(roleName))
                .toList();
    }

    private static List<CapabilityDTO> cachedDTOs(Locale locale) {
        // I18n.normalize 把 zh-CN/zh-TW/未知 lang 都收敛到白名单内,把 cache 上界锁死在 SUPPORTED_LANGS 数量
        String key = I18n.normalize(locale).getLanguage();
        return DTO_CACHE.computeIfAbsent(key,
                k -> ALL.stream().map(c -> toDTO(c, locale)).toList());
    }

    private static CapabilityDTO toDTO(Capability c, Locale locale) {
        // Capability 是 record 且包含 enum / Set<RoleType> 字段，BeanConvertUtils 不支持
        // record 源 + enum→String + Set→List 的隐式转换，用手工 setter 一次到位（且只有一个调用点）。
        CapabilityDTO d = new CapabilityDTO();
        d.setId(c.id());
        d.setDomain(c.domain());
        d.setRwClass(c.rwClass().name());
        d.setSensitivity(c.sensitivity().name());
        d.setDescription(locale == null ? I18n.t(c.description()) : I18n.t(c.description(), locale));
        d.setRequiredRoles(c.requiredRoles().stream().map(RoleType::name).sorted().toList());
        return d;
    }
}
