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

import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.service.metadata.MetadataService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@RequireViewer
public class MetadataController {

    private final MetadataService metadataService;

    @GetMapping("/{datasourceName}/catalogs")
    public Result<List<String>> listCatalogs(@PathVariable String datasourceName) {
        return Result.ok(metadataService.listCatalogs(datasourceName));
    }

    @GetMapping("/{datasourceName}/databases")
    public Result<List<String>> listDatabases(@PathVariable String datasourceName,
                                              @RequestParam(required = false) String catalog) {
        return Result.ok(metadataService.listDatabases(datasourceName, catalog));
    }

    @GetMapping("/{datasourceName}/databases/{database}/tables")
    public Result<List<TableMeta>> listTables(@PathVariable String datasourceName,
                                              @PathVariable String database,
                                              @RequestParam(required = false) String catalog) {
        return Result.ok(metadataService.listTables(datasourceName, catalog, database));
    }

    @GetMapping("/{datasourceName}/databases/{database}/tables/{table}/columns")
    public Result<List<ColumnMeta>> listColumns(@PathVariable String datasourceName,
                                                @PathVariable String database,
                                                @PathVariable String table,
                                                @RequestParam(required = false) String catalog) {
        return Result.ok(metadataService.listColumns(datasourceName, catalog, database, table));
    }

    @GetMapping("/{datasourceName}/databases/{database}/tables/{table}")
    public Result<TableDetail> getTableDetail(@PathVariable String datasourceName,
                                              @PathVariable String database,
                                              @PathVariable String table,
                                              @RequestParam(required = false) String catalog) {
        return Result.ok(metadataService.getTableDetail(datasourceName, catalog, database, table));
    }
}
