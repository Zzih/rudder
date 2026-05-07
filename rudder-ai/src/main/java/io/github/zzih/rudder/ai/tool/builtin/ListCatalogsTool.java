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

import lombok.RequiredArgsConstructor;

/**
 * 列出数据源的 catalog 列表。两层引擎(MySQL/Hive/Spark/Flink)返回空数组,直接用 list_databases 即可。
 * 三层引擎(Trino/StarRocks 挂外部 catalog)返回 catalog 名列表,后续 list_databases/tables 需带上 catalog 参数。
 */
@Component
@RequiredArgsConstructor
public class ListCatalogsTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "datasourceId":{"type":"integer","description":"Datasource id"}
                      },
                      "required":["datasourceId"]
                    }""");

    private final DatasourceService datasourceService;
    private final MetadataService metadataService;

    @Override
    public String name() {
        return "list_catalogs";
    }

    @Override
    public String description() {
        return "List catalog names in a 3-tier datasource (Trino, StarRocks with external catalogs). "
                + "Returns empty array for 2-tier engines (MySQL/Hive/Spark/Flink) — in that case skip to list_databases directly.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long dsId = input.get("datasourceId").asLong();
        var ds = datasourceService.getByIdWithWorkspace(ctx.getWorkspaceId(), dsId);
        var names = metadataService.listCatalogs(ds.getName());
        ArrayNode array = JsonUtils.createArrayNode();
        for (String n : names) {
            array.add(n);
        }
        return JsonUtils.toJson(array);
    }
}
