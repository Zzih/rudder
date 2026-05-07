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

package io.github.zzih.rudder.mcp.tool.metadata;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.WorkspaceGuard;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.service.metadata.MetadataService;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 元数据相关 MCP tools — 同 capability {@code metadata.browse} 下的多个只读操作聚合在一处。
 *
 * <p>每个方法通过 {@link McpTool} 注册为 MCP tool，{@link McpCapability} 关联到 capability，
 * 由 {@link io.github.zzih.rudder.mcp.tool.McpToolGuardAspect} 统一拦截做限流 / 双闸门 / 审计。
 */
@Service
@RequiredArgsConstructor
public class MetadataMcpTools {

    private final MetadataService metadataService;
    private final WorkspaceGuard workspaceGuard;

    @McpTool(name = "metadata.search", description = "Full-text search tables/columns by keyword across the given datasource.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("metadata.browse")
    public List<TableMeta> search(
                                  @McpToolParam(description = "datasource name", required = true) String datasourceName,
                                  @McpToolParam(description = "search keyword", required = true) String keyword) {
        workspaceGuard.requireDatasourceVisible(datasourceName);
        return metadataService.search(datasourceName, keyword);
    }

    @McpResource(uri = "rudder://datasource/{datasource}/catalog/{catalog}/database/{database}/tables", name = "rudder-tables", description = "Tables in the given database/catalog. For single-catalog engines pass catalog='-'. Discover databases via the rudder-datasource-databases resource.", mimeType = "application/json")
    @McpCapability("metadata.browse")
    public String listTables(String datasource, String catalog, String database) {
        workspaceGuard.requireDatasourceVisible(datasource);
        return JsonUtils
                .toJson(metadataService.listTables(datasource, WorkspaceGuard.unwrapCatalog(catalog), database));
    }

    @McpResource(uri = "rudder://datasource/{datasource}/catalog/{catalog}/database/{database}/table/{table}", name = "rudder-table", description = "Full table detail (columns + comment). For single-catalog engines pass catalog='-'. Discover table names via the rudder-tables resource or metadata.search tool.", mimeType = "application/json")
    @McpCapability("metadata.browse")
    public String getTable(String datasource, String catalog, String database, String table) {
        workspaceGuard.requireDatasourceVisible(datasource);
        return JsonUtils.toJson(
                metadataService.getTableDetail(datasource, WorkspaceGuard.unwrapCatalog(catalog), database, table));
    }

    @McpResource(uri = "rudder://datasource/{datasource}/catalog/{catalog}/database/{database}/table/{table}/columns", name = "rudder-columns", description = "Columns of the given table. For single-catalog engines pass catalog='-'. Equivalent to reading rudder-table and projecting just the columns array.", mimeType = "application/json")
    @McpCapability("metadata.browse")
    public String listColumns(String datasource, String catalog, String database, String table) {
        workspaceGuard.requireDatasourceVisible(datasource);
        return JsonUtils.toJson(
                metadataService.listColumns(datasource, WorkspaceGuard.unwrapCatalog(catalog), database, table));
    }
}
