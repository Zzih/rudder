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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * 把 SQL 解析成 {@link TableAccess} 列表,供 Local 鉴权按 (catalog, db, table) + action + columns 比对 grant。
 *
 * <p>覆盖语法:
 * <ul>
 *   <li>SELECT / WITH / UNION/INTERSECT/EXCEPT / 子查询 / JOIN → READ on 真实表</li>
 *   <li>INSERT INTO ... [SELECT ...] → 目标表 INSERT + 子查询表 READ</li>
 *   <li>UPDATE ... [FROM/WHERE ...] → 目标表 UPDATE + 子查询表 READ</li>
 *   <li>DELETE FROM ... [WHERE ...] → 目标表 DELETE + 子查询表 READ</li>
 * </ul>
 *
 * <p>列粒度:
 * <ul>
 *   <li>SELECT-list / WHERE / HAVING / GROUP BY / JOIN ON 中的限定列(prefix.col)按 FROM 子句的 alias→table 映射归属</li>
 *   <li>未限定列在单表作用域内归属唯一 FROM 表;多表作用域(JOIN)下整条 SELECT 的列被降级为表级(columns 清空)</li>
 *   <li>SELECT * / t.* 不收集任何具名列(等价于表级访问)</li>
 *   <li>INSERT INTO t(c1,c2) 与 UPDATE t SET c1=?,c2=? 显式列直接归属目标表</li>
 *   <li>外部 schema metadata 不可用,因此 ORDER BY 子句、CTE 内部列引用以 fail-open 处理(归属不确定时降级)</li>
 * </ul>
 *
 * <p>CTE 名(WITH 内定义的临时表)从输出剥除,避免误把临时表当真实表去鉴权。
 *
 * <p>DDL (CREATE/DROP/ALTER ...) 本地有意不鉴权:resolveStmt 只处理 DML/查询节点,不产出 DDL intent
 * (babel 仅能解析部分 DDL 如 CREATE TABLE,DROP/ALTER 等直接 parse 失败),两种情况都落 fail-open。
 * 作为低成本实现,DDL 的管控交由下游 DB 引擎或 Ranger 兜底。
 *
 * <p>解析失败统一 fail-open(返当前已收集结果),调用方按"未追溯到"处理。
 * 多语句脚本按 {@code ;} 拆分,逐条独立解析,单条失败不影响其他。
 */
@Slf4j
public final class SqlAccessResolver {

    private SqlAccessResolver() {
    }

    /** 解析 SQL,返回 access 列表(同 cat/db/t/action 自动合并 columns)。null/blank/全部解析失败返回空 list。 */
    public static List<TableAccess> resolve(String sql, SqlDialect dialect) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }
        Accumulator acc = new Accumulator();
        SqlParser.Config cfg = RudderSqlParser.babelConfig(dialect);
        for (String stmt : splitStatements(sql)) {
            if (stmt.isBlank()) {
                continue;
            }
            try {
                SqlNode root = SqlParser.create(stmt, cfg).parseStmt();
                Set<String> cteNames = new LinkedHashSet<>();
                collectCteNames(root, cteNames);
                resolveStmt(root, cteNames, acc);
            } catch (Exception e) {
                // Calcite SqlParseException.getMessage 自带一大段 "Was expecting one of:" grammar 候选,
                // 截到第一行就够定位 — 输出的是 "Encountered ... at line X, column Y" 这条核心信息。
                String msg = e.getMessage() == null ? "" : e.getMessage().split("\n", 2)[0];
                log.debug("SqlAccessResolver parse failed ({}): {}", e.getClass().getSimpleName(), msg);
            }
        }
        return acc.build();
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

    private static void resolveStmt(SqlNode root, Set<String> cteNames, Accumulator acc) {
        if (root == null) {
            return;
        }
        if (root instanceof SqlOrderBy ob) {
            resolveStmt(ob.query, cteNames, acc);
            return;
        }
        if (root instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    resolveQuery(wi.query, cteNames, acc);
                }
            }
            resolveStmt(with.body, cteNames, acc);
            return;
        }
        if (root instanceof SqlInsert ins) {
            MutableAccess target = addTableAccess(ins.getTargetTable(), TableAccess.Action.INSERT, cteNames, acc);
            if (target != null) {
                addExplicitColumns(ins.getTargetColumnList(), target);
            }
            resolveQuery(ins.getSource(), cteNames, acc);
            return;
        }
        if (root instanceof SqlUpdate upd) {
            MutableAccess target = addTableAccess(upd.getTargetTable(), TableAccess.Action.UPDATE, cteNames, acc);
            if (target != null) {
                addExplicitColumns(upd.getTargetColumnList(), target);
            }
            // UPDATE 的 WHERE/SET 表达式子查询里可能引用其他表,沿用原 fail-open 处理(无 scope, 表级 read)。
            collectReadFromExprBareTables(upd.getCondition(), cteNames, acc);
            return;
        }
        if (root instanceof SqlDelete del) {
            addTableAccess(del.getTargetTable(), TableAccess.Action.DELETE, cteNames, acc);
            collectReadFromExprBareTables(del.getCondition(), cteNames, acc);
            return;
        }
        // SELECT / UNION / 子查询 走 READ
        resolveQuery(root, cteNames, acc);
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

    // ==================== query 解析(带 scope 用于列归属) ====================

    private static void resolveQuery(SqlNode query, Set<String> cteNames, Accumulator acc) {
        if (query == null) {
            return;
        }
        if (query instanceof SqlOrderBy ob) {
            // ORDER BY 在 SELECT 外层,内层 scope 不可见,ORDER BY 列引用 fail-open(不收集列)。
            resolveQuery(ob.query, cteNames, acc);
            return;
        }
        if (query instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    resolveQuery(wi.query, cteNames, acc);
                }
            }
            resolveQuery(with.body, cteNames, acc);
            return;
        }
        if (query instanceof SqlSelect sel) {
            SelectScope scope = new SelectScope();
            resolveFromItem(sel.getFrom(), scope, cteNames, acc);
            collectColumnsFromExpr(sel.getSelectList(), scope, cteNames, acc);
            collectColumnsFromExpr(sel.getWhere(), scope, cteNames, acc);
            collectColumnsFromExpr(sel.getHaving(), scope, cteNames, acc);
            collectColumnsFromExpr(sel.getGroup(), scope, cteNames, acc);
            return;
        }
        if (query instanceof SqlCall call && isSetOp(call.getKind())) {
            for (SqlNode operand : call.getOperandList()) {
                resolveQuery(operand, cteNames, acc);
            }
        }
    }

    /**
     * 处理 FROM 项,递归 JOIN / AS / 子查询 / 真实表;给 scope 添加 alias 映射,JOIN ON 内列引用收集到 scope。
     */
    private static void resolveFromItem(SqlNode node, SelectScope scope, Set<String> cteNames, Accumulator acc) {
        if (node == null) {
            return;
        }
        if (node instanceof SqlJoin join) {
            resolveFromItem(join.getLeft(), scope, cteNames, acc);
            resolveFromItem(join.getRight(), scope, cteNames, acc);
            collectColumnsFromExpr(join.getCondition(), scope, cteNames, acc);
            return;
        }
        SqlNode source = node;
        String alias = null;
        if (node instanceof SqlBasicCall call && call.getKind() == SqlKind.AS) {
            source = call.operand(0);
            if (call.getOperandList().size() >= 2) {
                alias = unqualifiedName(call.operand(1));
            }
        }
        if (source instanceof SqlIdentifier id) {
            MutableAccess access = addTableAccess(id, TableAccess.Action.READ, cteNames, acc);
            if (access != null) {
                String aliasKey = alias != null && !alias.isEmpty() ? alias : lastName(id);
                scope.addTable(access, aliasKey);
            }
            return;
        }
        // 子查询(SqlSelect / SqlWith / SetOp):递归处理但其内部 scope 独立;
        // 外层引用其 alias.col 由于我们无 schema 反查列归属,fail-open(不收集到列粒度,表级 grant 通过)。
        resolveQuery(source, cteNames, acc);
    }

    // ==================== 列引用 visitor ====================

    /** 把表达式内所有 SqlIdentifier 当作列引用,按 scope 归属。SELECT 子句嵌套时进入子作用域。 */
    private static void collectColumnsFromExpr(SqlNode node, SelectScope scope, Set<String> cteNames, Accumulator acc) {
        if (node == null) {
            return;
        }
        // 嵌套子查询/CTE/SetOp:独立作用域,递归 resolveQuery,不污染当前 scope。
        if (node instanceof SqlSelect || node instanceof SqlWith || node instanceof SqlOrderBy
                || (node instanceof SqlCall sc && isSetOp(sc.getKind()))) {
            resolveQuery(node, cteNames, acc);
            return;
        }
        if (node instanceof SqlIdentifier id) {
            resolveColumn(id, scope);
            return;
        }
        if (node instanceof SqlBasicCall call && call.getKind() == SqlKind.AS) {
            // SELECT expr AS alias — 仅处理 expr,丢弃 alias
            if (!call.getOperandList().isEmpty()) {
                collectColumnsFromExpr(call.operand(0), scope, cteNames, acc);
            }
            return;
        }
        if (node instanceof SqlCall call) {
            for (SqlNode op : call.getOperandList()) {
                collectColumnsFromExpr(op, scope, cteNames, acc);
            }
            return;
        }
        if (node instanceof SqlNodeList list) {
            for (SqlNode n : list) {
                collectColumnsFromExpr(n, scope, cteNames, acc);
            }
        }
        // 其它 literal/字面量 直接忽略
    }

    /** 解析一个 SqlIdentifier 列引用并归属到 scope 内对应 MutableAccess。SELECT * / t.* 不收集。 */
    private static void resolveColumn(SqlIdentifier id, SelectScope scope) {
        List<String> names = id.names;
        String last = lastName(id);
        if (!isConcreteColumnName(last)) {
            return;
        }
        if (names.size() >= 2) {
            String prefix = names.get(names.size() - 2);
            MutableAccess target = scope.lookupAlias(prefix);
            if (target != null) {
                target.addColumn(last);
            }
            // 找不到 alias = outer scope / 未知,忽略(fail-open)
            return;
        }
        // 未限定列
        if (scope.singleTable()) {
            scope.singleAccess().addColumn(last);
        } else if (!scope.isEmpty()) {
            scope.degradeAll();
        }
        // scope 空(子查询?):不归属,fail-open
    }

    /** INSERT/UPDATE 显式列直接写到目标表 access。SqlNodeList 内每项应为 SqlIdentifier。 */
    private static void addExplicitColumns(SqlNodeList cols, MutableAccess target) {
        if (cols == null || cols.size() == 0) {
            return;
        }
        for (SqlNode n : cols) {
            if (n instanceof SqlIdentifier id) {
                String last = lastName(id);
                if (isConcreteColumnName(last)) {
                    target.addColumn(last);
                }
            }
        }
    }

    /**
     * UPDATE/DELETE 的 WHERE/SET 子查询里出现的真实表收集 READ(只到表级,不参与 scope 列归属)。
     * 沿用原 fail-open 行为:UPDATE/DELETE 主体不构造 scope,子查询表的列在子查询自己的 scope 内解析。
     */
    private static void collectReadFromExprBareTables(SqlNode expr, Set<String> cteNames, Accumulator acc) {
        if (expr == null) {
            return;
        }
        if (expr instanceof SqlSelect || expr instanceof SqlWith || expr instanceof SqlOrderBy
                || (expr instanceof SqlCall call && isSetOp(call.getKind()))) {
            resolveQuery(expr, cteNames, acc);
            return;
        }
        if (expr instanceof SqlCall call) {
            for (SqlNode operand : call.getOperandList()) {
                collectReadFromExprBareTables(operand, cteNames, acc);
            }
            return;
        }
        if (expr instanceof SqlNodeList list) {
            for (SqlNode n : list) {
                collectReadFromExprBareTables(n, cteNames, acc);
            }
        }
    }

    // ==================== 表 access 入口 ====================

    /**
     * 给定 FROM/目标表的 SqlIdentifier,加入 accumulator(同 cat/db/table/action 合并),返 MutableAccess 句柄供列归属;
     * 命中 CTE 名时返 null(不当真实表)。
     */
    private static MutableAccess addTableAccess(SqlNode tableNode,
                                                TableAccess.Action action,
                                                Set<String> cteNames,
                                                Accumulator acc) {
        if (!(tableNode instanceof SqlIdentifier id)) {
            return null;
        }
        List<String> names = id.names;
        String table = lastName(id);
        if (table == null || table.isEmpty()) {
            return null;
        }
        // CTE 名(WITH 内定义)不算真实表;只有 1 段且匹配时认为是 CTE。
        if (names.size() == 1 && cteNames.contains(table.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String database = names.size() >= 2 ? names.get(names.size() - 2) : null;
        String catalog = names.size() >= 3 ? names.get(names.size() - 3) : null;
        return acc.add(catalog, database, table, action);
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

    /** 取多段标识符的末段(table / column 名);空标识符返 null。 */
    private static String lastName(SqlIdentifier id) {
        List<String> names = id.names;
        return names.isEmpty() ? null : names.get(names.size() - 1);
    }

    /** 列名是否为具体值(非 null/空/"*" 通配)。 */
    private static boolean isConcreteColumnName(String col) {
        return col != null && !col.isEmpty() && !"*".equals(col);
    }

    // ==================== Accumulator + 状态对象 ====================

    /** 同 (catalog, database, table, action) 在多次访问间合并 columns 与顺序。 */
    private static final class Accumulator {

        private final Map<String, MutableAccess> map = new LinkedHashMap<>();

        MutableAccess add(String catalog, String database, String table, TableAccess.Action action) {
            String key = key(catalog, database, table, action);
            return map.computeIfAbsent(key, k -> new MutableAccess(catalog, database, table, action));
        }

        List<TableAccess> build() {
            List<TableAccess> out = new ArrayList<>(map.size());
            for (MutableAccess m : map.values()) {
                out.add(new TableAccess(m.catalog, m.database, m.table, List.copyOf(m.columns), m.action));
            }
            return out;
        }

        private static String key(String c, String d, String t, TableAccess.Action a) {
            return safeLower(c) + "|" + safeLower(d) + "|" + safeLower(t) + "|" + a;
        }

        private static String safeLower(String s) {
            return s == null ? "" : s.toLowerCase(Locale.ROOT);
        }
    }

    /** 一个 (cat, db, table, action) 的可变累加器:列收集 + degraded 标志。 */
    private static final class MutableAccess {

        final String catalog;
        final String database;
        final String table;
        final TableAccess.Action action;
        final Set<String> columns = new LinkedHashSet<>();
        boolean degraded;

        MutableAccess(String catalog, String database, String table, TableAccess.Action action) {
            this.catalog = catalog;
            this.database = database;
            this.table = table;
            this.action = action;
        }

        void addColumn(String col) {
            if (degraded || col == null || col.isEmpty()) {
                return;
            }
            columns.add(col);
        }

        /** 降级到表级:清空已收集列且后续 addColumn 无效。一旦多表作用域出现未限定列,该 access 不再适合列粒度判定。 */
        void degrade() {
            degraded = true;
            columns.clear();
        }
    }

    /** 单个 SELECT 作用域:alias→access 映射 + 此作用域直接出现的 READ 表集合。 */
    private static final class SelectScope {

        /** alias / 真实表名(均小写) → 对应 MutableAccess。 */
        private final Map<String, MutableAccess> aliasToAccess = new LinkedHashMap<>();
        /** 此作用域内直接出现的 READ entries(去重 — Self-join 同表名时只一份),用于未限定列归属判定。 */
        private final List<MutableAccess> directReads = new ArrayList<>();

        void addTable(MutableAccess access, String alias) {
            if (alias != null && !alias.isEmpty()) {
                aliasToAccess.put(alias.toLowerCase(Locale.ROOT), access);
            }
            if (!directReads.contains(access)) {
                directReads.add(access);
            }
        }

        MutableAccess lookupAlias(String prefix) {
            if (prefix == null || prefix.isEmpty()) {
                return null;
            }
            return aliasToAccess.get(prefix.toLowerCase(Locale.ROOT));
        }

        boolean isEmpty() {
            return directReads.isEmpty();
        }

        boolean singleTable() {
            return directReads.size() == 1;
        }

        MutableAccess singleAccess() {
            return directReads.get(0);
        }

        void degradeAll() {
            for (MutableAccess m : directReads) {
                m.degrade();
            }
        }
    }
}
