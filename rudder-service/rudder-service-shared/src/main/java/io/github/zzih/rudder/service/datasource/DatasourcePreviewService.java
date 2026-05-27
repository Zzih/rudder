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

package io.github.zzih.rudder.service.datasource;

import io.github.zzih.rudder.common.enums.error.DatasourceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.jdbc.JdbcConnections;
import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProviderRegistry;
import io.github.zzih.rudder.service.datasource.dto.DatasourcePreviewDTO;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.service.sink.CollectingResultSink;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;
import io.github.zzih.rudder.task.api.task.executor.SqlExecutor;

import java.sql.Statement;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源只读查询(预览 / AI readOnly)业务装配。datasource 模块(L3 基础设施)不该耦合
 * sink / redaction(L4 业务),所以装配住这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourcePreviewService {

    private final DatasourceService datasourceService;
    private final RedactionService redactionService;

    /**
     * 执行任意只读 SQL（SELECT / SHOW / DESC / EXPLAIN / WITH）。
     * <p>
     * 调用方(AI 工具 / 平台预览 API)先做 SQL 前缀校验;此处仅负责 JDBC 执行并强制 rows 截断。
     */
    public DatasourcePreviewDTO executeReadOnly(Long id, String sql, int rowCap) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(DatasourceErrorCode.DS_PREVIEW_SQL_EMPTY);
        }
        if (rowCap <= 0 || rowCap > 1000) {
            rowCap = 100;
        }
        Datasource ds = datasourceService.getById(id);
        DataSourceInfo info = datasourceService.getDataSourceInfo(id);
        return runViaSqlExecutor(ds, info, sql, rowCap, null, "Read-only query");
    }

    /** 预览表数据：执行带 LIMIT 的 SELECT *，返回列名和行数据。 */
    public DatasourcePreviewDTO preview(Long id, String database, String table, int limit) {
        validateIdentifier(database);
        validateIdentifier(table);
        if (limit <= 0 || limit > 1000) {
            limit = 100;
        }

        Datasource ds = datasourceService.getById(id);
        DataSourceInfo info = datasourceService.getDataSourceInfo(id);
        DatasourceTypeProvider provider = DatasourceTypeProviderRegistry.get(DatasourceType.of(info.getType()));
        String sql = provider.buildPreviewSql(database, table, limit);

        return runViaSqlExecutor(ds, info, sql, limit, database, "Preview");
    }

    /**
     * 统一只读执行入口。复用 Worker 侧 {@link SqlExecutor}:单点解析 alias / 联邦三段式 / CTE 等,
     * 输出带 originalTable/originalColumn 的 {@link ColumnMeta},sink 内部自动脱敏。
     */
    private DatasourcePreviewDTO runViaSqlExecutor(Datasource ds, DataSourceInfo info, String sql, int rowCap,
                                                   String defaultDatabase, String opLabel) {
        try {
            return JdbcConnections.runWith(info.getJdbcUrl(), info.toConnectionProperties(),
                    info.getDriverClass(), conn -> {
                        try (Statement stmt = conn.createStatement()) {
                            CollectingResultSink sink = new CollectingResultSink(redactionService);
                            DatasourceTypeProvider provider = DatasourceTypeProviderRegistry.get(
                                    DatasourceType.of(info.getType()));
                            SqlExecutor.execute(stmt, sql, rowCap, provider, ds.getName(), true, sink);
                            sink.close();
                            List<ColumnMeta> metas = sink.getColumnMetas();
                            if (defaultDatabase != null) {
                                for (ColumnMeta m : metas) {
                                    if (m.getDatabase() == null) {
                                        m.setDatabase(defaultDatabase);
                                    }
                                }
                            }
                            return DatasourcePreviewDTO.builder()
                                    .columns(metas.stream().map(ColumnMeta::getName).toList())
                                    .rows(sink.getRows())
                                    .columnMetas(metas)
                                    .build();
                        }
                    });
        } catch (Exception e) {
            log.error("{} failed for datasource {}", opLabel, ds.getId(), e);
            throw new BizException(DatasourceErrorCode.DS_CONNECTION_FAILED,
                    opLabel + " failed: " + e.getMessage(), e);
        }
    }

    private static void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(DatasourceErrorCode.DS_CONNECTION_FAILED,
                    "Invalid identifier: " + identifier);
        }
    }
}
