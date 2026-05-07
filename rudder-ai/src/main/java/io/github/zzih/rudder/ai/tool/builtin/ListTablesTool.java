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

/** 列出库下的表。 */
@Component
@RequiredArgsConstructor
public class ListTablesTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "datasourceId":{"type":"integer"},
                        "catalog":{"type":"string","description":"Required for Trino / StarRocks external catalogs. Call list_catalogs first."},
                        "database":{"type":"string"}
                      },
                      "required":["datasourceId","database"]
                    }""");

    private final DatasourceService datasourceService;
    private final MetadataService metadataService;

    @Override
    public String name() {
        return "list_tables";
    }

    @Override
    public String description() {
        return "List tables (with comments) in a given database. "
                + "Use this to discover tables before writing SQL.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long dsId = input.get("datasourceId").asLong();
        String database = input.get("database").asText();
        var ds = datasourceService.getByIdWithWorkspace(ctx.getWorkspaceId(), dsId);
        String catalog = input.has("catalog") && !input.get("catalog").isNull() ? input.get("catalog").asText() : null;
        var tables = metadataService.listTables(ds.getName(), catalog, database);
        ArrayNode array = JsonUtils.createArrayNode();
        for (var t : tables) {
            ObjectNode node = array.addObject();
            node.put("name", t.getName());
            if (t.getComment() != null) {
                node.put("comment", t.getComment());
            }
        }
        return JsonUtils.toJson(array);
    }
}
