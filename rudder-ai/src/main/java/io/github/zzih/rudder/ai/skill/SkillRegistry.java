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

package io.github.zzih.rudder.ai.skill;

import io.github.zzih.rudder.ai.dto.AiSkillDTO;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiSkillDao;
import io.github.zzih.rudder.dao.entity.AiSkill;
import io.github.zzih.rudder.llm.api.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Skill 注册表 —— skill 是纯定义,不带工作区归属。工作区可见性 + 权限规则在
 * {@code t_r_ai_tool_config} 里按 {@code skill__<name>} 配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    private final AiSkillDao skillDao;

    /** 返回全部 skill(含禁用)。admin 列表页用。 */
    public List<SkillDefinition> listAll() {
        List<AiSkill> dbRows = skillDao.selectAll();
        List<SkillDefinition> out = new ArrayList<>(dbRows.size());
        for (AiSkill row : dbRows) {
            out.add(toApi(row));
        }
        return out;
    }

    /** 返回所有启用的 skill。agent tool provider / skill picker 用。 */
    public List<SkillDefinition> listEnabled() {
        return listAll().stream()
                .filter(SkillDefinition::isEnabled)
                .toList();
    }

    /** 按名字查找,enabled=false 视为不存在。 */
    public Optional<SkillDefinition> find(String name) {
        AiSkill s = skillDao.selectByName(name);
        if (s == null || !Boolean.TRUE.equals(s.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(toApi(s));
    }

    // ==================== admin CRUD(供 Controller 调用) ====================

    public IPage<AiSkill> pageAdmin(int pageNum, int pageSize) {
        return skillDao.selectPage(pageNum, pageSize);
    }

    public AiSkill create(AiSkill body) {
        rejectSkillInRequiredTools(body.getRequiredTools());
        skillDao.insert(body);
        return body;
    }

    public void update(Long id, AiSkill body) {
        rejectSkillInRequiredTools(body.getRequiredTools());
        body.setId(id);
        skillDao.updateById(body);
    }

    public void delete(Long id) {
        skillDao.deleteById(id);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public IPage<AiSkillDTO> pageAdminDetail(int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(pageAdmin(pageNum, pageSize), AiSkillDTO.class);
    }

    public AiSkillDTO createDetail(AiSkillDTO body) {
        AiSkill entity = BeanConvertUtils.convert(body, AiSkill.class);
        return BeanConvertUtils.convert(create(entity), AiSkillDTO.class);
    }

    public void updateDetail(Long id, AiSkillDTO body) {
        update(id, BeanConvertUtils.convert(body, AiSkill.class));
    }

    /** 防递归:skill 的 requiredTools 里不允许出现另一个 skill(skill__ 前缀)。 */
    private static void rejectSkillInRequiredTools(String requiredToolsJson) {
        if (requiredToolsJson == null || requiredToolsJson.isBlank()) {
            return;
        }
        if (requiredToolsJson.contains("skill__")) {
            throw new io.github.zzih.rudder.common.exception.BizException(
                    io.github.zzih.rudder.common.enums.error.AiErrorCode.SKILL_REQUIRES_TOOL_NESTED);
        }
    }

    private SkillDefinition toApi(AiSkill e) {
        return SkillDefinition.builder()
                .id(e.getId())
                .name(e.getName())
                .displayName(e.getDisplayName())
                .description(e.getDescription())
                .category(e.getCategory())
                .promptTemplate(e.getDefinition() == null ? "" : e.getDefinition())
                .inputSchema(e.getInputSchema() == null ? null : JsonUtils.parseTree(e.getInputSchema()))
                .requiredTools(parseRequiredTools(e.getRequiredTools()))
                .modelOverride(e.getModelOverride())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .build();
    }

    private static List<String> parseRequiredTools(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = JsonUtils.parseTree(raw);
            if (node != null && node.isArray()) {
                List<String> out = new ArrayList<>(node.size());
                node.forEach(n -> out.add(n.asText()));
                return out;
            }
        } catch (Exception ignored) {
            // fall through to CSV
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
