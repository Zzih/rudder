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
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.metadata.MetadataService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** 返回指定表的列定义(名称、类型、注释、是否主键)。SQL 准确度的关键依赖。 */
@Component
@RequiredArgsConstructor
public class DescribeTableTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "datasourceId":{"type":"integer"},
                        "catalog":{"type":"string","description":"Required for Trino / StarRocks external catalogs. Call list_catalogs first."},
                        "database":{"type":"string"},
                        "table":{"type":"string"}
                      },
                      "required":["datasourceId","database","table"]
                    }""");

    private final DatasourceService datasourceService;
    private final MetadataService metadataService;

    @Override
    public String name() {
        return "describe_table";
    }

    @Override
    public String description() {
        return "Describe columns of a table: name, type, comment, isPrimaryKey. "
                + "ALWAYS call this before writing SQL against an unfamiliar table.";
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
        var ds = datasourceService.getByIdWithWorkspace(ctx.getWorkspaceId(), dsId);
        String catalog = input.has("catalog") && !input.get("catalog").isNull() ? input.get("catalog").asText() : null;
        var columns = metadataService.listColumns(ds.getName(), catalog, database, table);
        ArrayNode array = JsonUtils.createArrayNode();
        for (var c : columns) {
            ObjectNode node = array.addObject();
            node.put("name", c.getName());
            if (c.getType() != null) {
                node.put("type", c.getType());
            }
            if (c.getComment() != null) {
                node.put("comment", c.getComment());
            }
        }
        return JsonUtils.toJson(array);
    }
}
