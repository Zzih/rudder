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

package io.github.zzih.rudder.mcp.tool.knowledge;

import io.github.zzih.rudder.ai.rag.DocumentRetrievalService;
import io.github.zzih.rudder.ai.rag.dto.KnowledgeChunkDTO;
import io.github.zzih.rudder.ai.rag.dto.KnowledgeDocumentDTO;
import io.github.zzih.rudder.ai.rag.dto.KnowledgeDocumentDetailDTO;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.AiDocument;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.Page;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/**
 * 知识库（RAG）域 MCP tools — 把 Rudder 内部 RAG 能力暴露给外部 LLM 客户端。
 *
 * <p>提供「发现 → 浏览 → 检索 → 取全文」闭环：
 * <ul>
 *   <li>{@code knowledge.list_doc_types} — 列当前 workspace 已有的 docType 类目</li>
 *   <li>{@code knowledge.list_documents} — 按 docType 分页列文档元信息</li>
 *   <li>{@code knowledge.search} — 语义检索片段（向量/FULLTEXT 兜底）</li>
 *   <li>{@code knowledge.get_document} — 按 id 取单文档完整内容</li>
 *   <li>{@code rudder://knowledge/{id}} — 通过 URI 直读文档（resource 形态）</li>
 * </ul>
 *
 * <p>所有命中范围严格按 {@link UserContext#requireWorkspaceId()} workspace 过滤
 * （含「workspace_ids 为空 = 平台共享」语义），与 ContextBuilder 内部 RAG 看到的结果一致。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final DocumentRetrievalService retrievalService;

    @McpTool(name = "knowledge.search", description = "Semantic search over the workspace knowledge base. Returns top-K text chunks with source document id / title / docType / relevance score. Falls back to keyword (FULLTEXT) when vector store is unavailable.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("knowledge.search")
    public List<KnowledgeChunkDTO> search(
                                          @McpToolParam(description = "natural language query", required = true) String query,
                                          @McpToolParam(description = "optional document type filter (WIKI / SCRIPT / SCHEMA / METRIC_DEF / RUNBOOK); null = all types in workspace") String docType,
                                          @McpToolParam(description = "max chunks to return; default 5, hard-capped at 20") Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query required");
        }
        int limit = topK == null ? 5 : topK;
        return retrievalService
                .retrieve(UserContext.requireWorkspaceId(), nullIfBlank(docType), query, limit)
                .stream()
                .map(KnowledgeChunkDTO::from)
                .toList();
    }

    @McpTool(name = "knowledge.list_doc_types", description = "List the docType categories that actually exist in the current workspace's knowledge base (e.g. WIKI / SCRIPT / SCHEMA / METRIC_DEF / RUNBOOK). Use this before list_documents to know what categories are available.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("knowledge.search")
    public List<String> listDocTypes() {
        return retrievalService.listDocTypes(UserContext.requireWorkspaceId());
    }

    @McpTool(name = "knowledge.list_documents", description = "Page through knowledge base documents (metadata only — no body content). Use knowledge.get_document to fetch full content of a specific id.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("knowledge.search")
    public Page<KnowledgeDocumentDTO> listDocuments(
                                                    @McpToolParam(description = "filter by docType (WIKI / SCRIPT / SCHEMA / METRIC_DEF / RUNBOOK); null = all types") String docType,
                                                    @McpToolParam(description = "page number (1-based); default 1") Integer pageNum,
                                                    @McpToolParam(description = "page size (default 20, max 100)") Integer pageSize) {
        int p = pageNum == null ? 1 : pageNum;
        int s = pageSize == null ? 20 : pageSize;
        IPage<AiDocument> page = retrievalService.listDocuments(
                UserContext.requireWorkspaceId(), nullIfBlank(docType), p, s);
        List<KnowledgeDocumentDTO> rows = page.getRecords().stream()
                .map(KnowledgeDocumentDTO::from)
                .toList();
        return Page.of(page.getTotal(), (int) page.getCurrent(), (int) page.getSize(), rows);
    }

    @McpTool(name = "knowledge.get_document", description = "Fetch the full content of a single knowledge base document by id (discover ids via knowledge.list_documents or knowledge.search).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("knowledge.search")
    public KnowledgeDocumentDetailDTO getDocument(
                                                  @McpToolParam(description = "document id", required = true) Long id) {
        AiDocument doc = retrievalService.getDocument(UserContext.requireWorkspaceId(), id);
        return KnowledgeDocumentDetailDTO.from(doc);
    }

    @McpResource(uri = "rudder://knowledge/{id}", name = "rudder-knowledge-doc", description = "A knowledge base document in the current workspace (full content, addressed by id). Use knowledge.list_documents / knowledge.search to discover ids.", mimeType = "application/json")
    @McpCapability("knowledge.search")
    public String readDocument(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        long parsed;
        try {
            parsed = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid id: " + id);
        }
        AiDocument doc = retrievalService.getDocument(UserContext.requireWorkspaceId(), parsed);
        return JsonUtils.toJson(KnowledgeDocumentDetailDTO.from(doc));
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
