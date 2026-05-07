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

import org.apache.calcite.config.Lex;

/**
 * 平台支持的 SQL 方言。集中两个事:
 * <ol>
 *   <li>给 {@link SqlProjectionResolver} 提供 Calcite {@link Lex} 映射(列血缘解析用)</li>
 *   <li>给 {@code SqlExecutor.applyStreamingFetch} 提供 dialect 分发(JDBC 流式拉行的开关因驱动而异)</li>
 * </ol>
 *
 * <p>名字跟 {@code DatasourceType} 对齐(MYSQL/POSTGRES/HIVE/...),便于 String/enum 互转。
 */
public enum SqlDialect {

    MYSQL(Lex.MYSQL),
    POSTGRES(Lex.JAVA),
    HIVE(Lex.MYSQL_ANSI),
    TRINO(Lex.MYSQL_ANSI),
    CLICKHOUSE(Lex.MYSQL),
    DORIS(Lex.MYSQL),
    STARROCKS(Lex.MYSQL),
    SPARK(Lex.MYSQL_ANSI),
    FLINK(Lex.MYSQL_ANSI);

    private final Lex lex;

    SqlDialect(Lex lex) {
        this.lex = lex;
    }

    public Lex lex() {
        return lex;
    }

    /** 不区分大小写查找。未知 dialect 返回 null,调用方按默认 lex 兜底。 */
    public static SqlDialect of(String name) {
        return EnumUtils.lookupByName(SqlDialect.class, name).orElse(null);
    }
}
