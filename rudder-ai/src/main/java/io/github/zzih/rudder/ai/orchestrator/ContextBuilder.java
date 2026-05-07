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

package io.github.zzih.rudder.ai.orchestrator;

import io.github.zzih.rudder.ai.context.ContextProfileService;
import io.github.zzih.rudder.ai.context.PinnedTableService;
import io.github.zzih.rudder.ai.dialect.DialectService;
import io.github.zzih.rudder.ai.knowledge.EngineCompatibility;
import io.github.zzih.rudder.ai.orchestrator.turn.TurnRequest;
import io.github.zzih.rudder.common.enums.ai.SessionMode;
import io.github.zzih.rudder.common.utils.io.ClasspathResourceUtils;
import io.github.zzih.rudder.dao.entity.AiContextProfile;
import io.github.zzih.rudder.dao.entity.AiSession;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 构造 AI turn 的 system prompt。每个段落对应 {@code ai-prompts/sections/*.md} 里的一个 Spring AI
 * {@link PromptTemplate},按 {@code {variable}} 占位注入运行时值。
 * <p>
 * dialect 模板(starrocks/trino/...)仍为纯静态 .md,读入后直接 append。
 * RAG 段落由 Spring AI 2.0 {@code RetrievalAugmentationAdvisor} 链路独立处理,不在此拼装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int MAX_SELECTION_CHARS = 2_000;
    private static final int MAX_SCRIPT_CHARS = 4_000;

    private final ScriptService scriptService;
    private final DatasourceService datasourceService;
    private final PinnedTableService pinnedTableService;
    private final ContextProfileService contextProfileService;
    private final EngineCompatibility engineCompatibility;
    private final DialectService dialectService;

    private final Map<String, String> staticCache = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    public String build(TurnRequest req, AiSession session) {
        AiContextProfile profile = contextProfileService.resolve(
                req.getWorkspaceId(), session == null ? null : session.getId());
        return build(req, session, profile);
    }

    /** 调用方已经 resolve 好 profile 时直接走这条,避免重复查 DB。 */
    public String build(TurnRequest req, AiSession session, AiContextProfile profile) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(renderBaseRole(session, req.getWorkspaceId()));

        Datasource datasource = loadDatasource(req);
        TaskType tt = parseTaskType(req.getContextTaskType());
        if (DialectService.needsDialect(tt)) {
            String dialect = dialectService.loadContent(tt);
            if (!dialect.isBlank()) {
                sb.append("\n\n").append(dialect);
            }
        }

        if (Boolean.TRUE.equals(profile.getInjectOpenScript())) {
            appendActiveScript(sb, req);
        }
        if (Boolean.TRUE.equals(profile.getInjectSelection())) {
            appendSelection(sb, req);
        }
        appendPinnedTables(sb, req);
        appendDatasource(sb, datasource);
        appendEngineVisibility(sb, req);
        sb.append(loadSection("tool-guidelines"));
        appendCustomOverride(sb, session);

        return sb.toString();
    }

    /** 给 {@code RudderDocumentRetriever} 用 —— 查当前脚本引擎允许的 engineTypes,作为 RAG 过滤维度。 */
    public List<String> allowedEngines(TurnRequest req) {
        return engineCompatibility.allowedEngines(parseTaskType(req.getContextTaskType()));
    }

    // ==================== sections ====================

    private String renderBaseRole(AiSession session, long workspaceId) {
        String tplName = session != null && SessionMode.from(session.getMode()) == SessionMode.AGENT
                ? "base-role-agent"
                : "base-role-chat";
        return renderTemplate(tplName, Map.of("workspaceId", workspaceId));
    }

    private void appendActiveScript(StringBuilder sb, TurnRequest req) {
        if (req.getContextScriptCode() == null) {
            return;
        }
        try {
            Script script = scriptService.getByCode(req.getWorkspaceId(), req.getContextScriptCode());
            if (script == null) {
                return;
            }
            String content = script.getContent();
            if (content == null || content.isBlank()) {
                return;
            }
            sb.append(renderTemplate("active-script", Map.of(
                    "code", script.getCode() == null ? "" : String.valueOf(script.getCode()),
                    "name", safe(script.getName()),
                    "taskType", script.getTaskType() == null ? "" : script.getTaskType().name(),
                    "maxChars", MAX_SCRIPT_CHARS,
                    "content", truncate(content, MAX_SCRIPT_CHARS))));
        } catch (Exception e) {
            log.debug("context builder: load script {} failed: {}", req.getContextScriptCode(), e.getMessage());
        }
    }

    private void appendSelection(StringBuilder sb, TurnRequest req) {
        String sel = req.getContextSelection();
        if (sel == null || sel.isBlank()) {
            return;
        }
        sb.append(renderTemplate("selection", Map.of("selection", truncate(sel, MAX_SELECTION_CHARS))));
    }

    private void appendPinnedTables(StringBuilder sb, TurnRequest req) {
        Set<String> merged = new LinkedHashSet<>();
        if (req.getContextPinnedTables() != null) {
            merged.addAll(req.getContextPinnedTables());
        }
        if (req.getUserId() > 0) {
            merged.addAll(pinnedTableService.listRefs(PinnedTableService.SCOPE_USER, req.getUserId()));
        }
        if (req.getWorkspaceId() > 0) {
            merged.addAll(pinnedTableService.listRefs(PinnedTableService.SCOPE_WORKSPACE, req.getWorkspaceId()));
        }
        if (merged.isEmpty()) {
            return;
        }
        StringBuilder list = new StringBuilder();
        for (String ref : merged) {
            list.append("- ").append(ref).append("\n");
        }
        sb.append(renderTemplate("pinned-tables", Map.of("pinnedList", list.toString())));
    }

    private void appendEngineVisibility(StringBuilder sb, TurnRequest req) {
        TaskType tt = parseTaskType(req.getContextTaskType());
        if (tt == null) {
            return;
        }
        List<String> allowed = engineCompatibility.allowedEngines(tt);
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        sb.append(renderTemplate("engine-visibility", Map.of(
                "engine", tt.name(),
                "allowedList", String.join(", ", allowed))));
    }

    private void appendDatasource(StringBuilder sb, Datasource ds) {
        if (ds == null) {
            return;
        }
        sb.append(renderTemplate("datasource", Map.of(
                "id", ds.getId() == null ? "" : String.valueOf(ds.getId()),
                "name", safe(ds.getName()),
                "type", safe(ds.getDatasourceType()))));
    }

    private void appendCustomOverride(StringBuilder sb, AiSession session) {
        if (session == null) {
            return;
        }
        String custom = session.getSystemPromptOverride();
        if (custom == null || custom.isBlank()) {
            return;
        }
        sb.append(renderTemplate("custom-override", Map.of("custom", custom)));
    }

    // ==================== helpers ====================

    private Datasource loadDatasource(TurnRequest req) {
        if (req.getContextDatasourceId() == null) {
            return null;
        }
        try {
            return datasourceService.getByIdWithWorkspace(req.getWorkspaceId(), req.getContextDatasourceId());
        } catch (Exception e) {
            log.debug("context builder: load datasource {} failed: {}", req.getContextDatasourceId(), e.getMessage());
            return null;
        }
    }

    private TaskType parseTaskType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return TaskType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== static sections ====================

    private String loadSection(String name) {
        return staticCache.computeIfAbsent(name,
                k -> ClasspathResourceUtils.readTextOrEmpty("ai-prompts/sections/" + k + ".md"));
    }

    private String renderTemplate(String name, Map<String, Object> vars) {
        PromptTemplate tpl = templateCache.computeIfAbsent(name,
                k -> new PromptTemplate(ClasspathResourceUtils.readTextOrEmpty("ai-prompts/sections/" + k + ".md")));
        return tpl.render(vars);
    }

    private static String safe(String s) {
        return StringUtils.defaultString(s);
    }

    /** 超过 max 时截断并在末尾加信息量后缀,告诉 LLM 上下文被截断了多少字符。 */
    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n... [truncated " + (s.length() - max) + " chars]";
    }
}
