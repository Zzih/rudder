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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.request.DatasourceCreateRequest;
import io.github.zzih.rudder.api.response.DatasourceResponse;
import io.github.zzih.rudder.api.response.DatasourceWorkspaceGrantResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireSuperAdmin;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.WorkspaceDao;
import io.github.zzih.rudder.dao.entity.DatasourcePermission;
import io.github.zzih.rudder.dao.entity.Workspace;
import io.github.zzih.rudder.datasource.dto.DatasourceDTO;
import io.github.zzih.rudder.datasource.model.DataSourceCredentials;
import io.github.zzih.rudder.datasource.service.DatasourcePermissionService;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.metadata.api.model.TableSearchResult;
import io.github.zzih.rudder.service.metadata.MetadataService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final MetadataService metadataService;
    private final DatasourcePermissionService permissionService;
    private final WorkspaceDao workspaceDao;

    @PostMapping
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.CREATE, resourceType = AuditResourceType.DATASOURCE)
    public Result<DatasourceResponse> create(@Valid @RequestBody DatasourceCreateRequest request) {
        DatasourceDTO body = BeanConvertUtils.convert(request, DatasourceDTO.class);
        String credential = buildCredentialJson(request);
        return Result.ok(BeanConvertUtils.convert(
                datasourceService.createDetail(body, credential), DatasourceResponse.class));
    }

    /** 平台管理用:列出全部数据源(SUPER_ADMIN 才能查)。工作空间内的列表见 {@code GET /api/workspaces/:id/datasources}。 */
    @GetMapping
    @RequireSuperAdmin
    public Result<List<DatasourceResponse>> listAll() {
        return Result.ok(BeanConvertUtils.convertList(
                datasourceService.listAllDetail(), DatasourceResponse.class));
    }

    @GetMapping("/{id}")
    @RequireViewer
    public Result<DatasourceResponse> getById(@PathVariable Long id) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        if (workspaceId != null) {
            return Result.ok(BeanConvertUtils.convert(datasourceService.getByIdWithWorkspaceDetail(workspaceId, id),
                    DatasourceResponse.class));
        }
        return Result.ok(BeanConvertUtils.convert(datasourceService.getByIdDetail(id), DatasourceResponse.class));
    }

    @PutMapping("/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATASOURCE, resourceCode = "#id")
    public Result<DatasourceResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody DatasourceCreateRequest request) {
        DatasourceDTO body = BeanConvertUtils.convert(request, DatasourceDTO.class);
        String credential = buildCredentialJson(request);
        return Result.ok(BeanConvertUtils.convert(
                datasourceService.updateDetail(id, body, credential), DatasourceResponse.class));
    }

    @DeleteMapping("/{id}")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.DELETE, resourceType = AuditResourceType.DATASOURCE, resourceCode = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    @RequireDeveloper
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.TEST_CONNECTION, resourceType = AuditResourceType.DATASOURCE, description = "测试数据源连通性", resourceCode = "#id")
    public Result<Boolean> testConnection(@PathVariable Long id) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        datasourceService.resolveNameByWorkspace(workspaceId, id);
        return Result.ok(datasourceService.testConnection(id));
    }

    @GetMapping("/{id}/meta/catalogs")
    @RequireViewer
    public Result<List<String>> listCatalogs(@PathVariable Long id) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        return Result.ok(metadataService.listCatalogs(dsName));
    }

    @GetMapping("/{id}/meta/databases")
    @RequireViewer
    public Result<List<String>> listDatabases(@PathVariable Long id,
                                              @RequestParam(required = false) String catalog) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        return Result.ok(metadataService.listDatabases(dsName, catalog));
    }

    @GetMapping("/{id}/meta/databases/{db}/tables")
    @RequireViewer
    public Result<List<TableMeta>> listTables(@PathVariable Long id,
                                              @PathVariable String db,
                                              @RequestParam(required = false) String catalog) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        return Result.ok(metadataService.listTables(dsName, catalog, db));
    }

    @GetMapping("/{id}/meta/databases/{db}/tables/{table}/columns")
    @RequireViewer
    public Result<List<ColumnMeta>> listColumns(@PathVariable Long id,
                                                @PathVariable String db,
                                                @PathVariable String table,
                                                @RequestParam(required = false) String catalog) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        return Result.ok(metadataService.listColumns(dsName, catalog, db, table));
    }

    @GetMapping("/{id}/meta/search")
    @RequireViewer
    public Result<List<TableSearchResult>> searchTables(@PathVariable Long id,
                                                        @RequestParam String keyword) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        if (keyword == null || keyword.isBlank()) {
            return Result.ok(List.of());
        }
        List<TableMeta> raw = metadataService.search(dsName, keyword.trim());
        List<TableSearchResult> out = new java.util.ArrayList<>(raw.size());
        for (TableMeta t : raw) {
            String qualified = t.getName();
            // SPI 约定:搜索返回的 name 为 "db.table" 形式。用最后一个 '.' 切,
            // 保留 db 段中可能出现的 '.'(例:某些方言的 schema.database)。
            int cut = qualified == null ? -1 : qualified.lastIndexOf('.');
            if (cut > 0) {
                out.add(new TableSearchResult(qualified.substring(0, cut), qualified.substring(cut + 1),
                        t.getComment()));
            } else {
                out.add(new TableSearchResult(null, qualified, t.getComment()));
            }
        }
        return Result.ok(out);
    }

    @DeleteMapping("/{id}/meta/cache")
    @RequireDeveloper
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.REFRESH_META_CACHE, resourceType = AuditResourceType.DATASOURCE, description = "刷新元数据缓存", resourceCode = "#id")
    public Result<Void> refreshMetaCache(@PathVariable Long id) {
        Long workspaceId = UserContext.getWorkspaceIdOrNull();
        String dsName = datasourceService.resolveNameByWorkspace(workspaceId, id);
        metadataService.invalidateByDatasource(dsName);
        return Result.ok();
    }

    /** 列出某数据源已授权的工作空间(返回 id + name 让前端直接显示)。 */
    @GetMapping("/{id}/workspaces")
    @RequireSuperAdmin
    public Result<List<DatasourceWorkspaceGrantResponse>> listGrants(@PathVariable Long id) {
        // 触发存在性校验
        datasourceService.getByIdDetail(id);
        List<Long> wsIds = permissionService.listByDatasource(id).stream()
                .map(DatasourcePermission::getWorkspaceId)
                .toList();
        if (wsIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<Workspace> wss = workspaceDao.selectByIds(wsIds);
        return Result.ok(wss.stream()
                .map(w -> new DatasourceWorkspaceGrantResponse(w.getId(), w.getName()))
                .toList());
    }

    /** 用 workspaceIds 全量覆盖数据源的可见工作空间集合 (幂等)。 */
    @PutMapping("/{id}/workspaces")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.DATASOURCE, action = AuditAction.UPDATE, resourceType = AuditResourceType.DATASOURCE, description = "更新数据源工作空间授权", resourceCode = "#id")
    public Result<Void> setGrants(@PathVariable Long id, @RequestBody List<Long> workspaceIds) {
        datasourceService.getByIdDetail(id);
        Set<Long> ids = workspaceIds == null ? new HashSet<>() : new HashSet<>(workspaceIds);
        permissionService.setGrants(id, ids, UserContext.getUserId());
        return Result.ok();
    }

    private static String buildCredentialJson(DatasourceCreateRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return null;
        }
        DataSourceCredentials cred = new DataSourceCredentials();
        cred.setUsername(request.getUsername());
        cred.setPassword(request.getPassword());
        return JsonUtils.toJson(cred);
    }
}
