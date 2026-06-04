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

package io.github.zzih.rudder.common.sql;

import io.github.zzih.rudder.common.utils.bean.EnumUtils;

import com.alibaba.druid.DbType;

/**
 * 平台支持的 SQL 方言。集中两个事:
 * <ol>
 *   <li>给 SQL 解析器({@link SqlAccessResolver} / {@link SqlProjectionResolver} / {@link SqlLimitInjector})
 *       提供 Druid {@link DbType} 映射</li>
 *   <li>给 {@code SqlExecutor.applyStreamingFetch} 提供 dialect 分发(JDBC 流式拉行的开关因驱动而异)</li>
 * </ol>
 *
 * <p>名字跟 {@code DatasourceType} 对齐(MYSQL/POSTGRES/HIVE/...),便于 String/enum 互转。
 *
 * <p>FLINK 无对应 Druid 方言,落到 {@code hive}(Hive 系覆盖 Flink 的 {@code INSERT INTO/OVERWRITE} 写法)。
 */
public enum SqlDialect {

    MYSQL(DbType.mysql),
    POSTGRES(DbType.postgresql),
    HIVE(DbType.hive),
    TRINO(DbType.trino),
    CLICKHOUSE(DbType.clickhouse),
    DORIS(DbType.doris),
    STARROCKS(DbType.starrocks),
    SPARK(DbType.spark),
    FLINK(DbType.hive);

    private final DbType dbType;

    SqlDialect(DbType dbType) {
        this.dbType = dbType;
    }

    public DbType dbType() {
        return dbType;
    }

    /** 不区分大小写查找。未知 dialect 返回 null,调用方按默认 MySQL 方言兜底。 */
    public static SqlDialect of(String name) {
        return EnumUtils.lookupByName(SqlDialect.class, name).orElse(null);
    }
}
