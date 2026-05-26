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

import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.parser.SqlParser;

import lombok.extern.slf4j.Slf4j;

/**
 * 在顶层 SELECT 末尾追加 {@code LIMIT n}。仅当 Trino / Hive 这类分布式引擎 driver 把 JDBC
 * {@link java.sql.Statement#setMaxRows(int)} 实现为客户端截断时,server 仍按原 SQL 全表扫,需要把限制
 * 下推到 SQL 文本本身才能让 coordinator 感知。
 *
 * <p>AST 仅用于判定是否能追加(query 类型 + 无现成 LIMIT);改写靠字符串末尾拼接,不走 Calcite unparse,
 * 避免规范化破坏原 SQL 的注释 / 引号风格 / 大小写。LIMIT 前置换行,防止原 SQL 末尾的 {@code -- comment}
 * 或 {@code # comment} 行注释把 LIMIT 一起注释掉。
 *
 * <p>跳过场景:解析失败(方言扩展语法不被 Calcite babel 识别) / 非 query(INSERT/UPDATE/DELETE/SET/DDL) /
 * 顶层已有 LIMIT — 这些场景调用方仍可依赖 {@code setMaxRows} 客户端兜底。
 */
@Slf4j
public final class SqlLimitInjector {

    private SqlLimitInjector() {
    }

    /**
     * @param sql 原始 SQL,允许含末尾分号
     * @param limit 行数上限,非正数直接跳过
     * @param dialect 方言,null 走 MySQL lex
     * @return 追加 LIMIT 后的 SQL;不可追加时返回原 SQL
     */
    public static String inject(String sql, int limit, SqlDialect dialect) {
        if (sql == null || sql.isBlank() || limit <= 0) {
            return sql;
        }

        String trimmed = sql.strip();
        int trailingSemis = 0;
        String parseInput = trimmed;
        while (parseInput.endsWith(";")) {
            parseInput = parseInput.substring(0, parseInput.length() - 1).strip();
            trailingSemis++;
        }
        if (parseInput.isEmpty()) {
            return sql;
        }

        SqlNode node;
        try {
            node = SqlParser.create(parseInput, RudderSqlParser.babelConfig(dialect)).parseQuery();
        } catch (Exception e) {
            log.debug("Skip LIMIT injection, parse failed: {}", e.getMessage());
            return sql;
        }

        if (!shouldAppendLimit(node)) {
            return sql;
        }

        StringBuilder sb = new StringBuilder(parseInput.length() + 24)
                .append(parseInput)
                .append("\nLIMIT ")
                .append(limit);
        for (int i = 0; i < trailingSemis; i++) {
            sb.append(';');
        }
        log.info("Injected LIMIT {} into SQL (dialect={})", limit, dialect);
        return sb.toString();
    }

    private static boolean shouldAppendLimit(SqlNode node) {
        if (node == null) {
            return false;
        }
        if (node instanceof SqlSelect select) {
            return select.getFetch() == null;
        }
        if (node instanceof SqlOrderBy orderBy) {
            return orderBy.fetch == null;
        }
        if (node instanceof SqlWith with) {
            return shouldAppendLimit(with.body);
        }
        SqlKind k = node.getKind();
        return k == SqlKind.UNION || k == SqlKind.INTERSECT || k == SqlKind.EXCEPT;
    }
}
