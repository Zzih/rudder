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

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;

import lombok.extern.slf4j.Slf4j;

/**
 * 在顶层 SELECT 末尾追加 {@code LIMIT n}。仅当 Trino / Hive 这类分布式引擎 driver 把 JDBC
 * {@link java.sql.Statement#setMaxRows(int)} 实现为客户端截断时,server 仍按原 SQL 全表扫,需要把限制
 * 下推到 SQL 文本本身才能让 coordinator 感知。
 *
 * <p>AST 仅用于判定是否能追加(query 类型 + 无现成 LIMIT);改写靠字符串末尾拼接,不走 unparse,
 * 避免规范化破坏原 SQL 的注释 / 引号风格 / 大小写。LIMIT 前置换行,防止原 SQL 末尾的 {@code -- comment}
 * 行注释把 LIMIT 一起注释掉。
 *
 * <p>跳过场景:解析失败 / 非 query(INSERT/UPDATE/DELETE/SET/DDL)/ 顶层已有 LIMIT —
 * 这些场景调用方仍可依赖 {@code setMaxRows} 客户端兜底。
 */
@Slf4j
public final class SqlLimitInjector {

    private SqlLimitInjector() {
    }

    /**
     * @param sql 原始 SQL,允许含末尾分号
     * @param limit 行数上限,非正数直接跳过
     * @param dialect 方言,null 走 MySQL
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

        if (!shouldAppendLimit(parseInput, dialect)) {
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

    /** 单条 query(SELECT / UNION,含外层 WITH)且顶层无 LIMIT 才追加;解析失败 / 非 query 返回 false。 */
    private static boolean shouldAppendLimit(String sql, SqlDialect dialect) {
        List<SQLStatement> stmts;
        try {
            stmts = DruidSqlParser.parse(sql, dialect);
        } catch (Exception e) {
            log.debug("Skip LIMIT injection, parse failed: {}", e.getMessage());
            return false;
        }
        if (stmts.size() != 1 || !(stmts.get(0) instanceof SQLSelectStatement sel)) {
            return false;
        }
        SQLSelectQuery query = sel.getSelect().getQuery();
        if (query instanceof SQLSelectQueryBlock block) {
            return block.getLimit() == null;
        }
        if (query instanceof SQLUnionQuery union) {
            return union.getLimit() == null;
        }
        return false;
    }
}
