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
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 只读 SQL 工具 —— 仅允许 SELECT / SHOW / DESCRIBE 前缀,且强制 LIMIT ≤ 100。
 * 字段脱敏由 sink (CollectingResultSink) 内部完成,这里直接消费已脱敏的 rows。
 */
@Component
@RequiredArgsConstructor
public class RunSqlReadonlyTool extends AbstractBuiltinTool {

    private static final Pattern READONLY_PREFIX = Pattern.compile(
            "^\\s*(SELECT|SHOW|DESC(RIBE)?|EXPLAIN|WITH)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUTATION = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|MERGE|CALL)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "datasourceId":{"type":"integer"},
                        "sql":{"type":"string","description":"Read-only SQL (SELECT/SHOW/DESCRIBE/EXPLAIN/WITH). INSERT/UPDATE/DELETE/DDL are rejected."},
                        "limit":{"type":"integer","default":50,"description":"Row cap; the platform clamps to DatasourceService.INTERACTIVE_MAX_ROWS"}
                      },
                      "required":["datasourceId","sql"]
                    }""");

    private final DatasourceService datasourceService;
    private final DatasourcePreviewService datasourcePreviewService;

    @Override
    public String name() {
        return "run_sql_readonly";
    }

    @Override
    public String description() {
        return "Execute a read-only SQL (SELECT / SHOW / DESCRIBE / EXPLAIN / WITH) against a datasource "
                + "and return up to 100 rows. Results are redacted via the platform Redaction service before "
                + "being shown. DDL/DML statements are rejected.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long dsId = input.get("datasourceId").asLong();
        String sql = input.get("sql").asText().trim();
        int limit = input.hasNonNull("limit") ? input.get("limit").asInt() : 50;

        if (!READONLY_PREFIX.matcher(sql).find() || MUTATION.matcher(sql).find()) {
            throw new IllegalArgumentException(
                    "run_sql_readonly rejected: only SELECT/SHOW/DESC/EXPLAIN/WITH statements are allowed");
        }

        datasourceService.getByIdWithWorkspace(ctx.getWorkspaceId(), dsId);

        DatasourcePreviewDTO result = datasourcePreviewService.executeReadOnly(dsId, sql, limit);
        if (result == null || result.getColumns() == null) {
            return "No data: empty result set.";
        }
        // 字段脱敏在 sink 内部完成,这里返回的 rows 已脱敏

        List<String> cols = result.getColumns();
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            Object[] arr = new Object[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                arr[i] = row.get(cols.get(i));
            }
            rows.add(arr);
        }

        return "Rows: " + rows.size() + "\n" + MarkdownTables.render(cols, rows);
    }
}
