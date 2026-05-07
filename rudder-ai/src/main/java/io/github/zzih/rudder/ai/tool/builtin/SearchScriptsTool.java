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

package io.github.zzih.rudder.ai.tool.builtin;

import io.github.zzih.rudder.ai.rag.DocumentRetrievalService;
import io.github.zzih.rudder.ai.rag.DocumentRetrievalService.RetrievedChunk;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.ScriptService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * 在工作区脚本里搜索。
 * <p>
 * 优先走 RAG 语义召回(docType=SCRIPT);未索引 / Qdrant 不可用时降级到 name LIKE 关键词匹配。
 */
@Component
@RequiredArgsConstructor
public class SearchScriptsTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "query":{"type":"string","description":"Keyword or phrase to search"},
                        "limit":{"type":"integer","default":10}
                      },
                      "required":["query"]
                    }""");

    private final ScriptService scriptService;
    private final DocumentRetrievalService documentRetrievalService;

    @Override
    public String name() {
        return "search_scripts";
    }

    @Override
    public String description() {
        return "Find similar past scripts in the current workspace. "
                + "Uses semantic retrieval when embedding + Qdrant are configured, "
                + "otherwise falls back to name keyword matching. "
                + "Good for pulling few-shot references before authoring new scripts.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        String query = input.get("query").asText("");
        int limit = input.hasNonNull("limit") ? input.get("limit").asInt() : 10;
        ArrayNode array = JsonUtils.createArrayNode();

        List<RetrievedChunk> semantic = documentRetrievalService.retrieve(
                ctx.getWorkspaceId(), "SCRIPT", query, limit);
        Set<Long> seen = new HashSet<>();
        for (RetrievedChunk c : semantic) {
            if (c.getDocumentId() == null || !seen.add(c.getDocumentId())) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("documentId", c.getDocumentId());
            node.put("title", c.getTitle());
            node.put("score", c.getScore());
            node.put("excerpt", c.getChunkText() == null || c.getChunkText().length() <= 200
                    ? c.getChunkText()
                    : c.getChunkText().substring(0, 200) + "...");
        }

        if (array.size() < limit) {
            String keyword = query.toLowerCase();
            for (var s : scriptService.listByWorkspaceId(ctx.getWorkspaceId())) {
                if (array.size() >= limit) {
                    break;
                }
                if (s.getName() == null || !s.getName().toLowerCase().contains(keyword)) {
                    continue;
                }
                ObjectNode node = array.addObject();
                node.put("code", s.getCode());
                node.put("name", s.getName());
                if (s.getTaskType() != null) {
                    node.put("taskType", s.getTaskType().name());
                }
            }
        }
        return JsonUtils.toJson(array);
    }
}
