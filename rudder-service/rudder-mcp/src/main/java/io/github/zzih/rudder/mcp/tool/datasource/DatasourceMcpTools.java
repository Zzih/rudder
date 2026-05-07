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

package io.github.zzih.rudder.mcp.tool.datasource;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.datasource.dto.DatasourceDTO;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.mcp.tool.McpCapability;
import io.github.zzih.rudder.mcp.tool.WorkspaceGuard;
import io.github.zzih.rudder.mcp.tool.datasource.dto.TestConnectionResultDTO;
import io.github.zzih.rudder.service.metadata.MetadataService;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Datasource 域 MCP tools 聚合（view + manage + 元数据浏览）。 */
@Service
@RequiredArgsConstructor
public class DatasourceMcpTools {

    private final DatasourceService datasourceService;
    private final MetadataService metadataService;
    private final WorkspaceGuard workspaceGuard;

    @McpTool(name = "datasource.list", description = "List datasources visible to the current workspace (credentials redacted).", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("datasource.view")
    public List<DatasourceDTO> list() {
        return datasourceService.listByWorkspaceIdDetail(UserContext.requireWorkspaceId());
    }

    @McpResource(uri = "rudder://datasource/{name}", name = "rudder-datasource", description = "A datasource registered in the platform (credentials redacted). Use datasource.list to discover names.", mimeType = "application/json")
    @McpCapability("datasource.view")
    public String get(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        Long dsId = workspaceGuard.requireDatasourceVisible(name);
        return JsonUtils.toJson(datasourceService.getByIdDetail(dsId));
    }

    /** URI variable {name} 自动补全 —— 返回当前 workspace 可见且匹配前缀的 datasource name 列表。 */
    @McpComplete(uri = "rudder://datasource/{name}")
    public List<String> completeDatasourceName(String prefix) {
        return datasourceService.listByWorkspaceIdDetail(UserContext.requireWorkspaceId()).stream()
                .map(DatasourceDTO::getName)
                .filter(n -> n != null && (prefix == null || n.startsWith(prefix)))
                .limit(50)
                .toList();
    }

    @McpResource(uri = "rudder://datasource/{name}/catalogs", name = "rudder-datasource-catalogs", description = "Catalogs of the given datasource (Trino/Presto-style multi-catalog). For single-catalog engines (MySQL/Hive) the result is empty — use catalog='-' in nested URIs. Discover datasource names via datasource.list.", mimeType = "application/json")
    @McpCapability("datasource.view")
    public List<String> listCatalogs(String name) {
        workspaceGuard.requireDatasourceVisible(name);
        return metadataService.listCatalogs(name);
    }

    @McpResource(uri = "rudder://datasource/{name}/catalog/{catalog}/databases", name = "rudder-datasource-databases", description = "Databases (a.k.a. schemas) of the datasource under the given catalog. For single-catalog engines (MySQL/Hive) pass catalog='-'. Discover datasource names via datasource.list, catalogs via the /catalogs sibling resource.", mimeType = "application/json")
    @McpCapability("datasource.view")
    public List<String> listDatabases(String name, String catalog) {
        workspaceGuard.requireDatasourceVisible(name);
        return metadataService.listDatabases(name, WorkspaceGuard.unwrapCatalog(catalog));
    }

    @McpTool(name = "datasource.test_connection", description = "Test datasource connectivity. Returns ok=true if reachable, otherwise error reason.", annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    @McpCapability("datasource.test")
    public TestConnectionResultDTO testConnection(
                                                  @McpToolParam(description = "datasource name", required = true) String name) {
        Long dsId = workspaceGuard.requireDatasourceVisible(name);
        TestConnectionResultDTO result = new TestConnectionResultDTO();
        try {
            result.setOk(datasourceService.testConnection(dsId));
        } catch (Exception e) {
            result.setOk(false);
            result.setError(e.getMessage() == null ? "" : e.getMessage());
        }
        return result;
    }

    @McpTool(name = "datasource.create", description = "Create a datasource. credentialJson is encrypted server-side.")
    @McpCapability("datasource.manage")
    public DatasourceDTO create(
                                @McpToolParam(description = "Datasource connection info — name + datasourceType + host required", required = true) DatasourceDTO body,
                                @McpToolParam(description = "Credential JSON like {\"username\":\"...\", \"password\":\"...\"}; AES-encrypted at rest") String credentialJson) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (body.getDatasourceType() == null) {
            throw new IllegalArgumentException("datasourceType required");
        }
        return datasourceService.createDetail(body, credentialJson);
    }

    @McpTool(name = "datasource.update", description = "Update a datasource by id (credentialJson null keeps existing).", annotations = @McpTool.McpAnnotations(idempotentHint = true))
    @McpCapability("datasource.manage")
    public DatasourceDTO update(
                                @McpToolParam(description = "datasource id", required = true) Long id,
                                @McpToolParam(description = "Patch fields — only non-null fields take effect", required = true) DatasourceDTO body,
                                @McpToolParam(description = "New credential JSON; null = keep existing credential") String credentialJson) {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        return datasourceService.updateDetail(id, body, credentialJson);
    }

    @McpTool(name = "datasource.delete", description = "Delete a datasource by id (rejects if still referenced by scripts/tasks).", annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @McpCapability("datasource.manage")
    public void delete(
                       @McpToolParam(description = "datasource id", required = true) Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        datasourceService.delete(id);
    }
}
