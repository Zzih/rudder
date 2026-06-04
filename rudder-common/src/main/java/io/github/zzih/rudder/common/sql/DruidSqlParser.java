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

import java.util.List;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;

/**
 * 三个 SQL 解析器(access / projection / limit)共用的 Druid 解析入口。
 *
 * <p>方言特有 parser 比 MySQL 严格,会把 {@code comment} / {@code first} 等引擎合法的裸列名当保留字而整句解析失败;
 * 鉴权 / 血缘链路对解析失败是 fail-open(放行 / 丢血缘),等于这些常见列名静默绕过管控。故主方言失败时回退到
 * 标识符最宽容的 MySQL 方言再试一次:回退只可能多解析出结果,不会比主方言更差(两者皆失败仍抛主方言异常,
 * 调用方按原 fail-open 处理)。
 */
public final class DruidSqlParser {

    private DruidSqlParser() {
    }

    /** null dialect 走 MySQL(标识符最宽容)。default-dialect 策略集中在此,调用方不重复判定。 */
    public static List<SQLStatement> parse(String sql, SqlDialect dialect) {
        return parse(sql, dialect != null ? dialect.dbType() : DbType.mysql);
    }

    public static List<SQLStatement> parse(String sql, DbType dbType) {
        try {
            return SQLUtils.parseStatements(sql, dbType);
        } catch (RuntimeException primary) {
            if (dbType != DbType.mysql) {
                try {
                    return SQLUtils.parseStatements(sql, DbType.mysql);
                } catch (RuntimeException ignored) {
                    // 回退也失败 → 抛主方言异常,语义更贴近真实输入
                }
            }
            throw primary;
        }
    }
}
