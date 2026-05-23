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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.parser.SqlParser;

import lombok.extern.slf4j.Slf4j;

/**
 * 把 SQL 解析成 {@link TableAccess} 列表,供 Local 鉴权按 (catalog, db, table) + action 比对 grant。
 *
 * <p>覆盖语法:
 * <ul>
 *   <li>SELECT / WITH / UNION/INTERSECT/EXCEPT / 子查询 / JOIN → READ on 真实表</li>
 *   <li>INSERT INTO ... [SELECT ...] → 目标表 INSERT + 子查询表 READ</li>
 *   <li>UPDATE ... [FROM/WHERE ...] → 目标表 UPDATE + 子查询表 READ</li>
 *   <li>DELETE FROM ... [WHERE ...] → 目标表 DELETE + 子查询表 READ</li>
 * </ul>
 *
 * <p>CTE 名(WITH 内定义的临时表)从输出剥除,避免误把临时表当真实表去鉴权。
 *
 * <p>DDL (CREATE/DROP/ALTER) Calcite 默认 parser 不支持,parse 阶段直接抛 → 走 fail-open;
 * 这类语句的鉴权交由下游 DB 引擎或 Ranger 兜底,本地不拦。
 *
 * <p>解析失败统一 fail-open(返当前已收集结果),调用方按"未追溯到"处理。
 * 多语句脚本按 {@code ;} 拆分,逐条独立解析,单条失败不影响其他。
 */
@Slf4j
public final class SqlAccessResolver {

    private SqlAccessResolver() {
    }

    /** 解析 SQL,返回 access 列表。null/blank/解析失败返回空 list。 */
    public static List<TableAccess> resolve(String sql, SqlDialect dialect) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }
        List<TableAccess> out = new ArrayList<>();
        SqlParser.Config cfg = RudderSqlParser.babelConfig(dialect);
        for (String stmt : splitStatements(sql)) {
            if (stmt.isBlank()) {
                continue;
            }
            try {
                SqlNode root = SqlParser.create(stmt, cfg).parseStmt();
                Set<String> cteNames = new LinkedHashSet<>();
                collectCteNames(root, cteNames);
                resolveStmt(root, cteNames, out);
            } catch (Exception e) {
                // Calcite SqlParseException.getMessage 自带一大段 "Was expecting one of:" grammar 候选,
                // 截到第一行就够定位 — 输出的是 "Encountered ... at line X, column Y" 这条核心信息。
                String msg = e.getMessage() == null ? "" : e.getMessage().split("\n", 2)[0];
                log.debug("SqlAccessResolver parse failed ({}): {}", e.getClass().getSimpleName(), msg);
            }
        }
        return out;
    }

    /** 按裸 {@code ;} 拆多语句,字符串内的 {@code ;} 保留。简单状态机够 SQL 脚本场景。 */
    private static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                buf.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                buf.append(c);
                continue;
            }
            if (c == ';') {
                out.add(buf.toString().trim());
                buf.setLength(0);
                continue;
            }
            buf.append(c);
        }
        String tail = buf.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    // ==================== stmt 分发 ====================

    private static void resolveStmt(SqlNode root, Set<String> cteNames, List<TableAccess> out) {
        if (root == null) {
            return;
        }
        if (root instanceof SqlOrderBy ob) {
            resolveStmt(ob.query, cteNames, out);
            return;
        }
        if (root instanceof SqlWith with) {
            // WITH 内部的 SELECT 也要收集 READ,CTE 自身不当真实表
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    collectReadFromQuery(wi.query, cteNames, out);
                }
            }
            resolveStmt(with.body, cteNames, out);
            return;
        }
        if (root instanceof SqlInsert ins) {
            addTableAccess(ins.getTargetTable(), TableAccess.Action.INSERT, cteNames, out);
            collectReadFromQuery(ins.getSource(), cteNames, out);
            return;
        }
        if (root instanceof SqlUpdate upd) {
            addTableAccess(upd.getTargetTable(), TableAccess.Action.UPDATE, cteNames, out);
            collectReadFromExpr(upd.getCondition(), cteNames, out);
            return;
        }
        if (root instanceof SqlDelete del) {
            addTableAccess(del.getTargetTable(), TableAccess.Action.DELETE, cteNames, out);
            collectReadFromExpr(del.getCondition(), cteNames, out);
            return;
        }
        // SELECT / UNION / 子查询 走 READ
        collectReadFromQuery(root, cteNames, out);
    }

    // ==================== CTE 名收集 ====================

    private static void collectCteNames(SqlNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    out.add(unqualifiedName(wi.name).toLowerCase(Locale.ROOT));
                    collectCteNames(wi.query, out);
                }
            }
            collectCteNames(with.body, out);
            return;
        }
        if (node instanceof SqlOrderBy ob) {
            collectCteNames(ob.query, out);
            return;
        }
        if (node instanceof SqlSelect sel) {
            collectCteNames(sel.getFrom(), out);
            return;
        }
        if (node instanceof SqlInsert ins) {
            collectCteNames(ins.getSource(), out);
            return;
        }
        if (node instanceof SqlCall call) {
            for (SqlNode op : call.getOperandList()) {
                collectCteNames(op, out);
            }
        }
    }

    // ==================== READ 收集(SELECT 子树内的表引用) ====================

    private static void collectReadFromQuery(SqlNode query, Set<String> cteNames, List<TableAccess> out) {
        if (query == null) {
            return;
        }
        if (query instanceof SqlOrderBy ob) {
            collectReadFromQuery(ob.query, cteNames, out);
            return;
        }
        if (query instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    collectReadFromQuery(wi.query, cteNames, out);
                }
            }
            collectReadFromQuery(with.body, cteNames, out);
            return;
        }
        if (query instanceof SqlSelect sel) {
            collectFromItem(sel.getFrom(), cteNames, out);
            collectReadFromExpr(sel.getWhere(), cteNames, out);
            collectReadFromExpr(sel.getHaving(), cteNames, out);
            return;
        }
        if (query instanceof SqlCall call && isSetOp(call.getKind())) {
            for (SqlNode operand : call.getOperandList()) {
                collectReadFromQuery(operand, cteNames, out);
            }
        }
    }

    /** FROM 子树:递归 JOIN / AS / 子查询 / 真实表。 */
    private static void collectFromItem(SqlNode node, Set<String> cteNames, List<TableAccess> out) {
        if (node == null) {
            return;
        }
        if (node instanceof SqlJoin join) {
            collectFromItem(join.getLeft(), cteNames, out);
            collectFromItem(join.getRight(), cteNames, out);
            collectReadFromExpr(join.getCondition(), cteNames, out);
            return;
        }
        SqlNode source = node;
        if (node instanceof SqlBasicCall call && call.getKind() == SqlKind.AS) {
            source = call.operand(0);
        }
        if (source instanceof SqlIdentifier id) {
            addTableAccess(id, TableAccess.Action.READ, cteNames, out);
            return;
        }
        // 子查询(SqlSelect / SqlWith / SetOp)
        collectReadFromQuery(source, cteNames, out);
    }

    /** WHERE / HAVING / JOIN ON 内的 IN (SELECT ...) / EXISTS (SELECT ...) 子查询同样要拉表。 */
    private static void collectReadFromExpr(SqlNode expr, Set<String> cteNames, List<TableAccess> out) {
        if (expr == null) {
            return;
        }
        if (expr instanceof SqlSelect || expr instanceof SqlWith || expr instanceof SqlOrderBy
                || (expr instanceof SqlCall call && isSetOp(call.getKind()))) {
            collectReadFromQuery(expr, cteNames, out);
            return;
        }
        if (expr instanceof SqlCall call) {
            for (SqlNode operand : call.getOperandList()) {
                collectReadFromExpr(operand, cteNames, out);
            }
            return;
        }
        if (expr instanceof SqlNodeList list) {
            for (SqlNode n : list) {
                collectReadFromExpr(n, cteNames, out);
            }
        }
    }

    // ==================== 输出构造 ====================

    private static void addTableAccess(SqlNode tableNode,
                                       TableAccess.Action action,
                                       Set<String> cteNames,
                                       List<TableAccess> out) {
        if (!(tableNode instanceof SqlIdentifier id)) {
            return;
        }
        List<String> names = id.names;
        if (names.isEmpty()) {
            return;
        }
        String table = names.get(names.size() - 1);
        if (table == null || table.isEmpty()) {
            return;
        }
        // CTE 名(WITH 内定义)不算真实表;只有 1 段且匹配时认为是 CTE。
        if (names.size() == 1 && cteNames.contains(table.toLowerCase(Locale.ROOT))) {
            return;
        }
        String database = names.size() >= 2 ? names.get(names.size() - 2) : null;
        String catalog = names.size() >= 3 ? names.get(names.size() - 3) : null;
        out.add(new TableAccess(catalog, database, table, List.of(), action));
    }

    private static boolean isSetOp(SqlKind k) {
        return k == SqlKind.UNION || k == SqlKind.INTERSECT || k == SqlKind.EXCEPT;
    }

    private static String unqualifiedName(SqlNode n) {
        if (n instanceof SqlIdentifier id) {
            return id.getSimple();
        }
        return n == null ? "" : n.toString();
    }
}
