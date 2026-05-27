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

package io.github.zzih.rudder.spi.api.datasource;

import io.github.zzih.rudder.common.utils.bean.EnumUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rudder 平台支持的数据源类型。位于 {@code rudder-spi-api} 层,作为最底层的类型契约 —
 * task / datasource / service / ai 各层都依赖它做类型分发与持久化。
 *
 * <p>{@code datasource} 表 {@code datasource_type} 字段以字符串形式存储,运行时通过
 * {@link #of(String)} 反查 enum;DataHub URN 也以此名字为唯一标识,**新增类型不可破坏既有名字**。
 *
 * <p>更细的方言差异(MySQL 流式 fetch / Hive 反引号 / Flink 探活 SQL 等)由
 * {@code DatasourceTypeProvider} 多态承接,本 enum 只承担字段定义与 JDBC URL 拼装。
 */
@Getter
@AllArgsConstructor
public enum DatasourceType {

    HIVE("org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://%s:%d/%s", false),
    STARROCKS("com.starrocks.cj.jdbc.Driver", "jdbc:starrocks://%s:%d/%s", true),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s", false),
    DORIS("com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s", false),
    POSTGRES("org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", false),
    CLICKHOUSE("com.clickhouse.jdbc.ClickHouseDriver", "jdbc:clickhouse://%s:%d/%s", false),
    TRINO("io.trino.jdbc.TrinoDriver", "jdbc:trino://%s:%d/%s", true),
    SPARK("org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://%s:%d/%s", false),
    FLINK("org.apache.flink.table.jdbc.FlinkDriver", "jdbc:flink://%s:%d/%s", false);

    private final String driverClassName;
    private final String urlTemplate;

    /** 是否暴露 catalog 层:true = {@code catalog.database.table} 三层;false = {@code database.table} 两层。 */
    private final boolean hasCatalog;

    /** 不区分大小写的查找。接受 "MySQL"、"mysql"、"MYSQL" 等。未知抛 IllegalArgumentException。 */
    public static DatasourceType of(String name) {
        return EnumUtils.lookupByName(DatasourceType.class, name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown datasource type: " + name));
    }

    public String buildJdbcUrl(String host, int port, String database) {
        String db = (database != null && !database.isBlank()) ? database : "";
        String url = String.format(urlTemplate, host, port, db);
        return url.replaceAll("/$", "");
    }
}
