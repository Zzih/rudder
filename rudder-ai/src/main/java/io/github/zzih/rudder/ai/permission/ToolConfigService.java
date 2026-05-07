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

package io.github.zzih.rudder.ai.permission;

import io.github.zzih.rudder.ai.dto.AiToolConfigDTO;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiToolConfigDao;
import io.github.zzih.rudder.dao.entity.AiToolConfig;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 工具配置 CRUD + 查找。每个 {@code tool_name} 最多一行 config。
 * <p>
 * {@link #find(String, Long)} 返回**对指定 workspace 生效**的 config:
 * <ul>
 *   <li>config 行的 {@code workspace_ids} 为 null → 对所有 workspace 生效</li>
 *   <li>config 行的 {@code workspace_ids} 是 JSON 数组且包含 {@code workspaceId} → 对该 workspace 生效</li>
 *   <li>否则 → 此 workspace 不受该 config 影响,返回 null</li>
 * </ul>
 * 使用方(PermissionGate、ToolRegistry)据此判断可见性 + 规则覆盖。
 */
@Service
@RequiredArgsConstructor
public class ToolConfigService {

    private final AiToolConfigDao dao;

    /** 对指定 workspace 生效的 config。null=无 config 影响,走代码默认规则 + 默认可见。 */
    public AiToolConfig find(String toolName, Long workspaceId) {
        AiToolConfig cfg = dao.selectByToolName(toolName);
        if (cfg == null) {
            return null;
        }
        return matchesWorkspace(cfg, workspaceId) ? cfg : null;
    }

    /** 查全部 config(不按 workspace 过滤)。admin 列表 / Tools 总览用。 */
    public List<AiToolConfig> listAll() {
        return dao.selectAll();
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiToolConfig> pageAll(int pageNum, int pageSize) {
        return dao.selectPage(pageNum, pageSize);
    }

    public AiToolConfig create(AiToolConfig entity) {
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        dao.insert(entity);
        return entity;
    }

    public void update(Long id, AiToolConfig entity) {
        entity.setId(id);
        dao.updateById(entity);
    }

    public void delete(Long id) {
        dao.deleteById(id);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public com.baomidou.mybatisplus.core.metadata.IPage<AiToolConfigDTO> pageAllDetail(int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(pageAll(pageNum, pageSize), AiToolConfigDTO.class);
    }

    public AiToolConfigDTO createDetail(AiToolConfigDTO body) {
        AiToolConfig entity = BeanConvertUtils.convert(body, AiToolConfig.class);
        return BeanConvertUtils.convert(create(entity), AiToolConfigDTO.class);
    }

    public void updateDetail(Long id, AiToolConfigDTO body) {
        update(id, BeanConvertUtils.convert(body, AiToolConfig.class));
    }

    // ==================== helpers ====================

    /** workspace_ids 为 null → 全生效;数组含当前 workspaceId → 生效;否则不生效。 */
    public static boolean matchesWorkspace(AiToolConfig cfg, Long workspaceId) {
        String raw = cfg.getWorkspaceIds();
        if (raw == null || raw.isBlank() || "null".equals(raw.trim())) {
            return true;
        }
        if (workspaceId == null) {
            return false;
        }
        List<Long> ids = parseWorkspaceIds(raw);
        return ids.contains(workspaceId);
    }

    /** 解析 workspace_ids JSON 数组,容错:非数组或解析失败返回空列表。 */
    public static List<Long> parseWorkspaceIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = JsonUtils.parseTree(raw);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>(node.size());
            node.forEach(n -> {
                if (n.canConvertToLong()) {
                    out.add(n.asLong());
                }
            });
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
