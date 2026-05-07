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

package io.github.zzih.rudder.metadata.jdbc;

import io.github.zzih.rudder.common.jdbc.JdbcConnections;
import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.model.ColumnDetail;
import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC metadata client。三层统一模型:{@code catalog → database → table}。
 * 两层引擎(描述里 {@code hasCatalog=false},如 MySQL/Hive)的 catalog 参数为 {@code null}。
 *
 * <p>纯查询实现,不持缓存——缓存由宿主侧 {@code MetadataService} 统一做 cache-aside。
 * 数据源信息由调用方通过 {@link DataSourceInfo} 传入,provider 自包含,不回调宿主。
 */
@Slf4j
public class JdbcMetadataClient implements MetadataClient {

    public JdbcMetadataClient() {
    }

    // ==================== 3-tier API ====================

    @Override
    public List<String> listCatalogs(DataSourceInfo target) {
        if (!target.isHasCatalog()) {
            return List.of();
        }
        List<String> catalogs = new ArrayList<>();
        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            try (ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    catalogs.add(rs.getString("TABLE_CAT"));
                }
            }
            // Trino/StarRocks 某些驱动行为差异:getCatalogs 不稳定时 fallback SHOW CATALOGS
            if (catalogs.isEmpty()) {
                try (
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SHOW CATALOGS")) {
                    while (rs.next()) {
                        catalogs.add(rs.getString(1));
                    }
                } catch (SQLException ignored) {
                    // provider 不支持 SHOW CATALOGS,返回空
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list catalogs for {}", target.getName(), e);
        }
        return catalogs;
    }

    @Override
    public List<String> listDatabases(DataSourceInfo target, String catalog) {
        boolean hasCatalog = target.isHasCatalog();
        List<String> databases = new ArrayList<>();
        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            if (hasCatalog && catalog != null) {
                // 三层引擎:getSchemas(catalog, null)
                try (ResultSet rs = conn.getMetaData().getSchemas(catalog, null)) {
                    while (rs.next()) {
                        databases.add(rs.getString("TABLE_SCHEM"));
                    }
                } catch (SQLException | AbstractMethodError ignored) {
                    // fallback SHOW SCHEMAS FROM <catalog>(Trino) / USE <catalog> + SHOW DATABASES
                    databases.addAll(showSchemasOrDatabases(conn, catalog));
                }
            } else {
                // 两层引擎:沿用 getCatalogs → getSchemas → SHOW DATABASES 的 fallback 链
                try (ResultSet rs = conn.getMetaData().getCatalogs()) {
                    while (rs.next()) {
                        databases.add(rs.getString("TABLE_CAT"));
                    }
                }
                if (databases.isEmpty()) {
                    try (ResultSet rs = conn.getMetaData().getSchemas()) {
                        while (rs.next()) {
                            String schema = rs.getString("TABLE_SCHEM");
                            if (!databases.contains(schema)) {
                                databases.add(schema);
                            }
                        }
                    }
                }
                if (databases.isEmpty()) {
                    try (
                            Statement stmt = conn.createStatement();
                            ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                        while (rs.next()) {
                            databases.add(rs.getString(1));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list databases for {}:{}", target.getName(), catalog, e);
        }
        return databases;
    }

    @Override
    public List<TableMeta> listTables(DataSourceInfo target, String catalog, String database) {
        List<TableMeta> tables = new ArrayList<>();
        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            try {
                // getTables(catalog, schemaPattern, tableNamePattern, types) —— catalog 为 null 时驱动自行处理
                ResultSet rs = conn.getMetaData().getTables(catalog, database, "%",
                        new String[]{"TABLE", "VIEW"});
                while (rs.next()) {
                    TableMeta meta = new TableMeta();
                    meta.setCatalog(catalog);
                    meta.setDatabase(database);
                    meta.setName(rs.getString("TABLE_NAME"));
                    meta.setComment(rs.getString("REMARKS"));
                    tables.add(meta);
                }
                rs.close();
            } catch (UnsupportedOperationException e) {
                log.debug("getTables() not supported, falling back to SHOW TABLES for {}:{}.{}",
                        target.getName(), catalog, database);
                try (Statement stmt = conn.createStatement()) {
                    if (catalog != null) {
                        stmt.execute("USE `" + catalog + "`.`" + database + "`");
                    } else {
                        stmt.execute("USE `" + database + "`");
                    }
                    try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                        while (rs.next()) {
                            TableMeta meta = new TableMeta();
                            meta.setCatalog(catalog);
                            meta.setDatabase(database);
                            meta.setName(rs.getString(1));
                            tables.add(meta);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list tables for {}:{}.{}", target.getName(), catalog, database, e);
        }
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(DataSourceInfo target, String catalog, String database, String table) {
        List<ColumnMeta> columns = new ArrayList<>();
        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            try {
                ResultSet rs = conn.getMetaData().getColumns(catalog, database, table, "%");
                while (rs.next()) {
                    ColumnMeta meta = new ColumnMeta();
                    meta.setName(rs.getString("COLUMN_NAME"));
                    meta.setType(rs.getString("TYPE_NAME"));
                    meta.setComment(rs.getString("REMARKS"));
                    columns.add(meta);
                }
                rs.close();
            } catch (UnsupportedOperationException e) {
                log.debug("getColumns() not supported, falling back to DESCRIBE for {}:{}.{}.{}",
                        target.getName(), catalog, database, table);
                try (Statement stmt = conn.createStatement()) {
                    if (catalog != null) {
                        stmt.execute("USE `" + catalog + "`.`" + database + "`");
                    } else {
                        stmt.execute("USE `" + database + "`");
                    }
                    try (ResultSet rs = stmt.executeQuery("DESCRIBE `" + table + "`")) {
                        while (rs.next()) {
                            ColumnMeta meta = new ColumnMeta();
                            meta.setName(rs.getString(1));
                            meta.setType(rs.getString(2));
                            columns.add(meta);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list columns for {}:{}.{}.{}", target.getName(), catalog, database, table, e);
        }
        return columns;
    }

    @Override
    public TableDetail getTableDetail(DataSourceInfo target, String catalog, String database, String table) {
        TableDetail detail = new TableDetail();
        detail.setDatasourceName(target.getName());
        detail.setCatalog(catalog);
        detail.setDatabase(database);
        detail.setTableName(table);

        List<ColumnDetail> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            boolean useFallback = false;
            try {
                try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, database, table)) {
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                    }
                }
                try (ResultSet rs = conn.getMetaData().getColumns(catalog, database, table, "%")) {
                    while (rs.next()) {
                        ColumnDetail col = new ColumnDetail();
                        col.setName(rs.getString("COLUMN_NAME"));
                        col.setType(rs.getString("TYPE_NAME"));
                        col.setDescription(rs.getString("REMARKS"));
                        col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                        col.setPrimaryKey(primaryKeys.contains(col.getName()));
                        columns.add(col);
                    }
                }
                try (ResultSet rs = conn.getMetaData().getTables(catalog, database, table, null)) {
                    if (rs.next()) {
                        detail.setDescription(rs.getString("REMARKS"));
                    }
                }
            } catch (UnsupportedOperationException e) {
                useFallback = true;
            }

            if (useFallback) {
                log.debug("DatabaseMetaData not supported, falling back to DESCRIBE for {}:{}.{}.{}",
                        target.getName(), catalog, database, table);
                try (Statement stmt = conn.createStatement()) {
                    if (catalog != null) {
                        stmt.execute("USE `" + catalog + "`.`" + database + "`");
                    } else {
                        stmt.execute("USE `" + database + "`");
                    }
                    try (ResultSet rs = stmt.executeQuery("DESCRIBE `" + table + "`")) {
                        while (rs.next()) {
                            ColumnDetail col = new ColumnDetail();
                            col.setName(rs.getString(1));
                            col.setType(rs.getString(2));
                            col.setNullable(true);
                            col.setPrimaryKey(false);
                            columns.add(col);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get table detail for {}:{}.{}.{}", target.getName(), catalog, database, table, e);
        }

        detail.setColumns(columns);
        return detail;
    }

    @Override
    public List<TableMeta> search(DataSourceInfo target, String keyword) {
        boolean threeTier = target.isHasCatalog();
        List<TableMeta> tables = new ArrayList<>();
        try (
                Connection conn = JdbcConnections.open(target.getJdbcUrl(), target.getUsername(), target.getPassword(),
                        target.getDriverClass())) {
            DatabaseMetaData metaData = conn.getMetaData();
            // 第一维枚举:三层引擎用 catalog,两层用 getCatalogs 返回的 database 名
            List<String> firstLevel = new ArrayList<>();
            try (ResultSet rs = metaData.getCatalogs()) {
                while (rs.next()) {
                    firstLevel.add(rs.getString("TABLE_CAT"));
                }
            }
            try {
                for (String first : firstLevel) {
                    String cat = threeTier ? first : null;
                    String dbPattern = threeTier ? null : first;
                    try (
                            ResultSet rs = metaData.getTables(cat, dbPattern,
                                    "%" + keyword + "%", new String[]{"TABLE", "VIEW"})) {
                        while (rs.next()) {
                            TableMeta meta = new TableMeta();
                            meta.setCatalog(cat);
                            meta.setDatabase(rs.getString("TABLE_SCHEM"));
                            if (meta.getDatabase() == null && !threeTier) {
                                meta.setDatabase(first);
                            }
                            meta.setName(rs.getString("TABLE_NAME"));
                            meta.setComment(rs.getString("REMARKS"));
                            tables.add(meta);
                        }
                    }
                }
            } catch (UnsupportedOperationException e) {
                log.debug("getTables() not supported for search, falling back to SHOW TABLES for {}", target.getName());
                try (Statement stmt = conn.createStatement()) {
                    for (String first : firstLevel) {
                        stmt.execute("USE `" + first + "`");
                        try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                            while (rs.next()) {
                                String tableName = rs.getString(1);
                                if (tableName.toLowerCase().contains(keyword.toLowerCase())) {
                                    TableMeta meta = new TableMeta();
                                    meta.setDatabase(first);
                                    meta.setName(tableName);
                                    tables.add(meta);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search tables with keyword {} in {}", keyword, target.getName(), e);
        }
        return tables;
    }

    // ==================== helpers ====================

    /** 三层引擎在 {@code getSchemas(catalog, null)} 不支持时的 fallback。 */
    private List<String> showSchemasOrDatabases(Connection conn, String catalog) {
        List<String> out = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SHOW SCHEMAS FROM `" + catalog + "`")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
                return out;
            } catch (SQLException ignored) {
                // 接着试 USE <catalog> + SHOW DATABASES
            }
            stmt.execute("USE `" + catalog + "`");
            try (ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ignored) {
            // 全 fail 返回空
        }
        return out;
    }
}
