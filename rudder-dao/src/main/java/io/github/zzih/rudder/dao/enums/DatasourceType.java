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

package io.github.zzih.rudder.dao.enums;

import io.github.zzih.rudder.common.utils.bean.EnumUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DatasourceType {

    HIVE("org.apache.hive.jdbc.HiveDriver", "jdbc:hive2://%s:%d/%s", false),
    STARROCKS("com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&useServerPrepStmts=false&cachePrepStmts=false&useInformationSchema=false",
            true),
    MYSQL("com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useInformationSchema=true", false),
    DORIS("com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true",
            false),
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
        // 当 database 为空时移除查询参数前的尾部斜杠
        // 例如 jdbc:mysql://host:9030/?params → jdbc:mysql://host:9030?params
        return url.replace("/?", "?").replaceAll("/$", "");
    }
}
