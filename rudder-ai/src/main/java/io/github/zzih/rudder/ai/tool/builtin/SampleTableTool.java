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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.datasource.dto.DatasourcePreviewDTO;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.datasource.DatasourcePreviewService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 读取表的前 N 行样本,渲染成 Markdown 交给 AI。
 * 字段脱敏由 sink (CollectingResultSink) 内部完成,这里直接消费已脱敏的 rows。
 */
@Component
@RequiredArgsConstructor
public class SampleTableTool extends AbstractBuiltinTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "datasourceId":{"type":"integer"},
                        "database":{"type":"string"},
                        "table":{"type":"string"},
                        "limit":{"type":"integer","default":5}
                      },
                      "required":["datasourceId","database","table"]
                    }""");

    private final DatasourceService datasourceService;
    private final DatasourcePreviewService datasourcePreviewService;

    @Override
    public String name() {
        return "sample_table";
    }

    @Override
    public String description() {
        return "Fetch the first N rows of a table (PII columns are redacted before reaching the AI). "
                + "Use this to understand value patterns before writing SQL. Hard cap 20 rows.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long dsId = input.get("datasourceId").asLong();
        String database = input.get("database").asText();
        String table = input.get("table").asText();
        int limit = input.hasNonNull("limit") ? input.get("limit").asInt() : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(limit, MAX_LIMIT));

        datasourceService.getByIdWithWorkspace(ctx.getWorkspaceId(), dsId);

        DatasourcePreviewDTO preview = datasourcePreviewService.preview(dsId, database, table, limit);
        if (preview == null || preview.getColumns() == null || preview.getColumns().isEmpty()) {
            return "No data: table returned zero columns.";
        }
        // 字段脱敏在 sink 内部完成,这里返回的 rows 已脱敏

        List<String> cols = preview.getColumns();
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> row : preview.getRows()) {
            Object[] arr = new Object[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                arr[i] = row.get(cols.get(i));
            }
            rows.add(arr);
        }

        return MarkdownTables.render(cols, rows);
    }
}
