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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.ai.dialect.DialectService;
import io.github.zzih.rudder.ai.dto.AiMcpServerDTO;
import io.github.zzih.rudder.ai.dto.AiMetadataSyncConfigDTO;
import io.github.zzih.rudder.ai.dto.AiSkillDTO;
import io.github.zzih.rudder.ai.dto.AiToolConfigDTO;
import io.github.zzih.rudder.ai.knowledge.MetadataSyncService;
import io.github.zzih.rudder.ai.knowledge.MetadataSyncService.SyncResult;
import io.github.zzih.rudder.ai.mcp.SpringAiMcpClientManager;
import io.github.zzih.rudder.ai.permission.ToolConfigService;
import io.github.zzih.rudder.ai.skill.SkillRegistry;
import io.github.zzih.rudder.ai.tool.ToolOverviewService;
import io.github.zzih.rudder.api.request.AiDialectUpdateRequest;
import io.github.zzih.rudder.api.request.AiMcpServerRequest;
import io.github.zzih.rudder.api.request.AiMetadataSyncConfigRequest;
import io.github.zzih.rudder.api.request.AiSkillRequest;
import io.github.zzih.rudder.api.request.AiToolConfigRequest;
import io.github.zzih.rudder.api.response.AiDialectSlotResponse;
import io.github.zzih.rudder.api.response.AiMcpServerResponse;
import io.github.zzih.rudder.api.response.AiMetadataSyncConfigResponse;
import io.github.zzih.rudder.api.response.AiSkillResponse;
import io.github.zzih.rudder.api.response.AiToolConfigResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.enums.ai.SkillCategory;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.llm.api.skill.SkillDefinition;
import io.github.zzih.rudder.service.mcp.McpClientManager;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI 后台管理聚合 controller,汇总所有 SUPER_ADMIN 负责的"N 条配置实例"CRUD:
 * <ul>
 *   <li>/api/ai/mcp/** —— MCP server 多实例(每条是一个 MCP server 配置)</li>
 *   <li>/api/ai/admin/tool-permissions/** —— 工具权限覆盖</li>
 *   <li>/api/ai/metadata-sync/** —— 每数据源的元数据同步配置</li>
 *   <li>/api/ai/skills/** —— 平台 / 工作区级 skill 定义(用户可读,admin 可写)</li>
 * </ul>
 * 前端 URL 保持原有路径,方法级 {@code @RequireRole} 区分 SUPER_ADMIN / DEVELOPER。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final SpringAiMcpClientManager mcpManager;
    private final McpClientManager mcpClientManager;
    private final ToolConfigService toolConfigService;
    private final MetadataSyncService metadataSyncService;
    private final SkillRegistry skillRegistry;
    private final ToolOverviewService toolOverviewService;
    private final DialectService dialectService;
    private final io.github.zzih.rudder.ai.rag.RagDebugService ragDebugService;

    // ==================== MCP servers ====================

    @GetMapping("/mcp/servers")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<IPage<AiMcpServerResponse>> listMcp(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                mcpManager.pageDetail(pageNum, pageSize), AiMcpServerResponse.class));
    }

    @PostMapping("/mcp/servers")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiMcpServerResponse> createMcp(@Valid @RequestBody AiMcpServerRequest request) {
        AiMcpServerDTO dto = mcpManager.createDetail(BeanConvertUtils.convert(request, AiMcpServerDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiMcpServerResponse.class));
    }

    @PutMapping("/mcp/servers/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> updateMcp(@PathVariable Long id, @Valid @RequestBody AiMcpServerRequest request) {
        mcpManager.updateDetail(id, BeanConvertUtils.convert(request, AiMcpServerDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/mcp/servers/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> deleteMcp(@PathVariable Long id) {
        mcpManager.delete(id);
        return Result.ok();
    }

    @PostMapping("/mcp/refresh-health")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> refreshMcpHealth() {
        mcpClientManager.refreshHealth();
        return Result.ok();
    }

    // ==================== Tools 总览(只读聚合)====================

    /**
     * 返回 admin 可见的全部 tool(NATIVE + MCP + SKILL),每条附带默认权限 + 权限覆盖。
     * 用于 Tools 总览页 + Skills 选择器 + MCP allowlist 选择器。
     */
    @GetMapping("/tools")
    @RequireRole(RoleType.DEVELOPER)
    public Result<List<ToolOverviewService.ToolView>> listTools(
                                                                @RequestParam(required = false) String source,
                                                                @RequestParam(required = false, defaultValue = "false") boolean excludeSkill) {
        io.github.zzih.rudder.llm.api.tool.AgentTool.ToolSource filter = null;
        if (source != null && !source.isBlank()) {
            try {
                filter = io.github.zzih.rudder.llm.api.tool.AgentTool.ToolSource.valueOf(source.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 未知 source 不过滤,退回全部
            }
        }
        return Result.ok(toolOverviewService.list(filter, excludeSkill));
    }

    // ==================== Tool configs(原 tool_permissions)====================

    @GetMapping("/admin/tool-configs")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<IPage<AiToolConfigResponse>> listToolConfigs(@RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                toolConfigService.pageAllDetail(pageNum, pageSize), AiToolConfigResponse.class));
    }

    @PostMapping("/admin/tool-configs")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiToolConfigResponse> createToolConfig(@Valid @RequestBody AiToolConfigRequest request) {
        AiToolConfigDTO dto = toolConfigService.createDetail(BeanConvertUtils.convert(request, AiToolConfigDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiToolConfigResponse.class));
    }

    @PutMapping("/admin/tool-configs/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> updateToolConfig(@PathVariable Long id, @Valid @RequestBody AiToolConfigRequest request) {
        toolConfigService.updateDetail(id, BeanConvertUtils.convert(request, AiToolConfigDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/admin/tool-configs/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> deleteToolConfig(@PathVariable Long id) {
        toolConfigService.delete(id);
        return Result.ok();
    }

    // ==================== Dialect prompts ====================

    /**
     * 列出所有需要方言指导的 TaskType,每一项附带 label / 当前 content / 是否有 DB 覆盖。
     * 前端按此渲染"方言"配置 tab —— 新增 TaskType 零前端改动。
     */
    @GetMapping("/admin/dialects")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<List<AiDialectSlotResponse>> listDialects() {
        List<AiDialectSlotResponse> out = new ArrayList<>();
        for (TaskType tt : dialectService.listSlots()) {
            boolean overridden = dialectService.hasOverride(tt);
            String content = dialectService.loadContent(tt);
            out.add(new AiDialectSlotResponse(tt.name(), tt.getLabel(), content, overridden));
        }
        return Result.ok(out);
    }

    @PutMapping("/admin/dialects/{taskType}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> updateDialect(@PathVariable String taskType, @RequestBody AiDialectUpdateRequest request) {
        dialectService.upsertOverride(taskType, request.getContent(),
                request.getEnabled() == null || request.getEnabled());
        return Result.ok();
    }

    /** 删除 DB 覆盖,回退到 classpath 出厂默认。 */
    @DeleteMapping("/admin/dialects/{taskType}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> resetDialect(@PathVariable String taskType) {
        dialectService.resetToDefault(taskType);
        return Result.ok();
    }

    // ==================== Metadata sync ====================

    @GetMapping("/metadata-sync")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<IPage<AiMetadataSyncConfigResponse>> listMetadataSync(
                                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                                        @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                metadataSyncService.pageDetail(pageNum, pageSize), AiMetadataSyncConfigResponse.class));
    }

    @GetMapping("/metadata-sync/by-datasource/{datasourceId}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiMetadataSyncConfigResponse> getMetadataSyncByDatasource(@PathVariable Long datasourceId) {
        return Result.ok(BeanConvertUtils.convert(
                metadataSyncService.getByDatasourceIdDetail(datasourceId), AiMetadataSyncConfigResponse.class));
    }

    @PostMapping("/metadata-sync")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiMetadataSyncConfigResponse> saveMetadataSync(
                                                                 @Valid @RequestBody AiMetadataSyncConfigRequest request) {
        AiMetadataSyncConfigDTO dto = metadataSyncService.saveDetail(
                BeanConvertUtils.convert(request, AiMetadataSyncConfigDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiMetadataSyncConfigResponse.class));
    }

    @PutMapping("/metadata-sync/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiMetadataSyncConfigResponse> updateMetadataSync(@PathVariable Long id,
                                                                   @Valid @RequestBody AiMetadataSyncConfigRequest request) {
        request.setId(id);
        AiMetadataSyncConfigDTO dto = metadataSyncService.saveDetail(
                BeanConvertUtils.convert(request, AiMetadataSyncConfigDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiMetadataSyncConfigResponse.class));
    }

    @DeleteMapping("/metadata-sync/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> deleteMetadataSync(@PathVariable Long id) {
        metadataSyncService.delete(id);
        return Result.ok();
    }

    @PostMapping("/metadata-sync/sync/{datasourceId}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<SyncResult> runMetadataSync(@PathVariable Long datasourceId) {
        return Result.ok(metadataSyncService.syncByDatasourceId(datasourceId));
    }

    // ==================== Skills ====================

    /** Developer 侧:给 AI 聊天窗口的 skill picker 用,只返回启用的。 */
    @GetMapping("/skills")
    @RequireRole(RoleType.DEVELOPER)
    public Result<List<SkillDefinition>> listSkills() {
        // Skill 不再按 workspace 过滤,工作区可见性在 Tools 页通过 tool_config 管理
        return Result.ok(skillRegistry.listEnabled());
    }

    @GetMapping("/skills/admin")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<IPage<AiSkillResponse>> listSkillsAdmin(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                skillRegistry.pageAdminDetail(pageNum, pageSize), AiSkillResponse.class));
    }

    @PostMapping("/skills")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<AiSkillResponse> createSkill(@Valid @RequestBody AiSkillRequest request) {
        request.setCategory(SkillCategory.from(request.getCategory()).name());
        AiSkillDTO dto = skillRegistry.createDetail(BeanConvertUtils.convert(request, AiSkillDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiSkillResponse.class));
    }

    @PutMapping("/skills/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> updateSkill(@PathVariable Long id, @Valid @RequestBody AiSkillRequest request) {
        request.setCategory(SkillCategory.from(request.getCategory()).name());
        skillRegistry.updateDetail(id, BeanConvertUtils.convert(request, AiSkillDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/skills/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> deleteSkill(@PathVariable Long id) {
        skillRegistry.delete(id);
        return Result.ok();
    }

    /** 前端 skill 表单用的分类下拉 options。返回枚举 name 数组(i18n 在前端做)。 */
    @GetMapping("/skills/categories")
    @RequireRole(RoleType.DEVELOPER)
    public Result<List<String>> listSkillCategories() {
        return Result.ok(java.util.Arrays.stream(SkillCategory.values())
                .map(Enum::name)
                .toList());
    }

    // ==================== RAG retrieval debug ====================

    /**
     * 输入 query → 手动跑生产 RAG 链路各 stage,返回每阶段 input/output/duration/error trace。
     * 仅 SUPER_ADMIN: trace 包含 retrieved chunks 内容,可能含敏感数据。
     */
    @PostMapping("/rag/debug")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<io.github.zzih.rudder.ai.rag.RagDebugTrace> debugRag(
                                                                       @Valid @RequestBody io.github.zzih.rudder.api.request.RagDebugRequest request) {
        io.github.zzih.rudder.task.api.task.enums.TaskType tt = request.getTaskType() == null ? null
                : io.github.zzih.rudder.task.api.task.enums.TaskType.valueOf(request.getTaskType());
        return Result.ok(ragDebugService.debug(request.getQuery(), request.getWorkspaceId(), tt));
    }
}
