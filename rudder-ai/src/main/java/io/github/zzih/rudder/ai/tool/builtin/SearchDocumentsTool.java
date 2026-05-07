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

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** RAG 语义检索:Wiki / Runbook / 指标定义 / 其他 AI 文档。降级走 MySQL FULLTEXT。 */
@Component
@RequiredArgsConstructor
public class SearchDocumentsTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "query":{"type":"string","description":"自然语言查询,AI 会把问题改写成适合检索的形式"},
                        "docType":{"type":"string","description":"可选过滤:WIKI|SCRIPT|SCHEMA|METRIC_DEF|RUNBOOK"},
                        "topK":{"type":"integer","default":5,"description":"返回 chunk 上限(最多 20)"}
                      },
                      "required":["query"]
                    }""");

    private final DocumentRetrievalService retrieval;

    @Override
    public String name() {
        return "search_documents";
    }

    @Override
    public String description() {
        return "Semantic search across indexed knowledge (wiki / runbooks / metric definitions / schemas). "
                + "Falls back to MySQL FULLTEXT keyword search when no embedding provider is configured. "
                + "Use this tool to ground answers in project-specific documentation before writing SQL.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        String query = input.get("query").asText();
        String docType = input.hasNonNull("docType") ? input.get("docType").asText() : null;
        int topK = input.hasNonNull("topK") ? input.get("topK").asInt() : 5;

        List<RetrievedChunk> chunks = retrieval.retrieve(ctx.getWorkspaceId(), docType, query, topK);
        if (chunks.isEmpty()) {
            return "No matching documents. (If semantic search is expected, ensure an EMBEDDING provider and Qdrant are configured.)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Retrieved ").append(chunks.size()).append(" chunk(s):\n\n");
        for (RetrievedChunk c : chunks) {
            sb.append("### [").append(c.getDocType()).append("] ").append(c.getTitle())
                    .append("  (score=").append(String.format("%.3f", c.getScore())).append(")\n");
            sb.append(c.getChunkText()).append("\n\n");
        }
        return sb.toString();
    }
}
