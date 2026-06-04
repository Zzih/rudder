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

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLExistsExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLInSubQueryExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import com.alibaba.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertInto;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLMergeStatement;
import com.alibaba.druid.sql.ast.statement.SQLReplaceStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.ast.statement.SQLUnionQueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUpdateSetItem;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.hive.ast.HiveInsert;
import com.alibaba.druid.sql.dialect.hive.ast.HiveMultiInsertStatement;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveLoadDataStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * 把 SQL 解析成 {@link TableAccess} 列表,供 Local 鉴权按 (catalog, db, table) + action + columns 比对 grant。
 *
 * <p>覆盖语法:
 * <ul>
 *   <li>SELECT / WITH / UNION/INTERSECT/EXCEPT / 子查询 / JOIN → READ on 真实表</li>
 *   <li>INSERT INTO ... [SELECT ...] → 目标表 INSERT + 子查询表 READ(含 Hive {@code INSERT [INTO|OVERWRITE] TABLE t PARTITION(...)})</li>
 *   <li>UPDATE ... [WHERE ...] → 目标表 UPDATE + 子查询表 READ</li>
 *   <li>DELETE FROM ... [WHERE ...] → 目标表 DELETE + 子查询表 READ</li>
 *   <li>MERGE INTO ... USING ... → 目标表 UPDATE/INSERT(按子句)+ 源表 READ</li>
 *   <li>REPLACE INTO ... → 目标表 INSERT + 子查询表 READ</li>
 *   <li>Hive multi-insert({@code FROM s INSERT ... INSERT ...})→ 各目标表 INSERT + 源表 READ</li>
 *   <li>LOAD DATA ... INTO TABLE t → 目标表 INSERT</li>
 * </ul>
 *
 * <p>列粒度:SELECT-list / WHERE / HAVING / GROUP BY / JOIN ON 中的限定列(prefix.col)按 FROM 子句的
 * alias→table 映射归属;未限定列在单表作用域内归属唯一 FROM 表,多表作用域(JOIN)下整条 SELECT 的列降级为表级
 * (columns 清空);{@code SELECT *} / {@code t.*} 不收集任何具名列(等价表级)。INSERT 目标列表与 UPDATE SET
 * 列直接归属目标表。
 *
 * <p>CTE 名(WITH 内定义)从输出剥除,避免误把临时表当真实表鉴权。
 *
 * <p>DDL:CREATE → 目标表 CREATE(CTAS 另把源表当 READ);DROP → 各表 DROP;ALTER → 目标表 ALTER。
 * access 名由 {@code PluginType.accessFor} 按 plugin 映射。
 *
 * <p>多语句脚本按 {@code ;} 拆分(字符串内 {@code ;} 保留),逐条独立解析,单条解析失败 fail-open(跳过),
 * 不影响其他语句。
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
        for (String stmt : splitStatements(sql)) {
            if (stmt.isBlank()) {
                continue;
            }
            try {
                for (SQLStatement s : DruidSqlParser.parse(stmt, dialect)) {
                    resolveStmt(s, acc);
                }
            } catch (Exception e) {
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

    private static void resolveStmt(SQLStatement stmt, Accumulator acc) {
        if (stmt instanceof SQLSelectStatement sel) {
            resolveSelect(sel.getSelect(), Collections.emptySet(), acc);
        } else if (stmt instanceof SQLInsertInto ins) {
            MutableAccess target = addTableAccess(ins.getTableSource(), TableAccess.Action.INSERT, acc);
            if (target != null) {
                addExplicitColumns(ins.getColumns(), target);
            }
            if (ins.getQuery() != null) {
                resolveSelect(ins.getQuery(), Collections.emptySet(), acc);
            }
        } else if (stmt instanceof SQLUpdateStatement upd) {
            if (upd.getTableSource() instanceof SQLExprTableSource) {
                MutableAccess target = addTableAccess(upd.getTableSource(), TableAccess.Action.UPDATE, acc);
                if (target != null) {
                    for (SQLUpdateSetItem item : upd.getItems()) {
                        addColumnExpr(item.getColumn(), target);
                    }
                }
            } else {
                // 多表 UPDATE(JOIN / 逗号形式):各目标表表级 UPDATE
                addWriteTargets(upd.getTableSource(), TableAccess.Action.UPDATE, acc);
            }
            addSourceRead(upd.getFrom(), acc); // UPDATE ... FROM/USING 源表 READ(Postgres 等)
            collectSubqueryReads(upd.getWhere(), acc);
        } else if (stmt instanceof SQLDeleteStatement del) {
            // getTableSource 而非 getExprTableSource:多表 DELETE 目标是 JOIN,后者会 ClassCastException → fail-open
            addWriteTargets(del.getTableSource(), TableAccess.Action.DELETE, acc);
            addSourceRead(del.getUsing(), acc);
            collectSubqueryReads(del.getWhere(), acc);
        } else if (stmt instanceof SQLCreateTableStatement create) {
            addTableAccess(create.getTableSource(), TableAccess.Action.CREATE, acc);
            if (create.getSelect() != null) {
                resolveSelect(create.getSelect(), Collections.emptySet(), acc);
            }
        } else if (stmt instanceof SQLDropTableStatement drop) {
            for (SQLExprTableSource t : drop.getTableSources()) {
                addTableAccess(t, TableAccess.Action.DROP, acc);
            }
        } else if (stmt instanceof SQLAlterTableStatement alter) {
            addTableAccess(alter.getTableSource(), TableAccess.Action.ALTER, acc);
        } else if (stmt instanceof SQLMergeStatement merge) {
            // 目标按出现的子句产出写动作(UPDATE / INSERT);两者皆无时按 UPDATE 兜底。源(USING)按 READ。
            var updClause = merge.getUpdateClause();
            var insClause = merge.getInsertClause();
            if (updClause != null || insClause == null) {
                addTableAccess(merge.getInto(), TableAccess.Action.UPDATE, acc);
            }
            if (insClause != null) {
                addTableAccess(merge.getInto(), TableAccess.Action.INSERT, acc);
            }
            addSourceRead(merge.getUsing(), acc);
            // ON 条件及 WHEN 子句条件里可能含对其他表的相关子查询,按 READ 收
            collectSubqueryReads(merge.getOn(), acc);
            if (updClause != null) {
                collectSubqueryReads(updClause.getWhere(), acc);
            }
            if (insClause != null) {
                collectSubqueryReads(insClause.getWhere(), acc);
            }
        } else if (stmt instanceof SQLReplaceStatement replace) {
            MutableAccess target = addTableAccess(replace.getTableSource(), TableAccess.Action.INSERT, acc);
            if (target != null) {
                addExplicitColumns(replace.getColumns(), target);
            }
            if (replace.getQuery() != null) {
                resolveSelect(replace.getQuery().getSubQuery(), Collections.emptySet(), acc);
            }
        } else if (stmt instanceof HiveMultiInsertStatement multi) {
            addSourceRead(multi.getFrom(), acc);
            for (HiveInsert item : multi.getItems()) {
                MutableAccess target = addTableAccess(item.getTableSource(), TableAccess.Action.INSERT, acc);
                if (target != null) {
                    addExplicitColumns(item.getColumns(), target);
                }
                // 各分支 SELECT 的 WHERE 子查询可能引用其他表,按 READ 收
                if (item.getQuery() != null) {
                    resolveSelect(item.getQuery(), Collections.emptySet(), acc);
                }
            }
        } else if (stmt instanceof HiveLoadDataStatement load) {
            addTableAccess(load.getInto(), TableAccess.Action.INSERT, acc);
        }
    }

    /** 把一个源 table source(USING / multi-insert FROM)按 READ 收集,复用 FROM 项解析。 */
    private static void addSourceRead(SQLTableSource src, Accumulator acc) {
        addFromItem(src, new SelectScope(), Collections.emptySet(), acc);
    }

    /** 写目标可能是 JOIN(多表 UPDATE/DELETE):递归到各基表,逐个产出写动作(表级)。 */
    private static void addWriteTargets(SQLTableSource src, TableAccess.Action action, Accumulator acc) {
        if (src instanceof SQLJoinTableSource join) {
            addWriteTargets(join.getLeft(), action, acc);
            addWriteTargets(join.getRight(), action, acc);
        } else {
            addTableAccess(src, action, acc);
        }
    }

    // ==================== READ 查询解析(带 scope 列归属) ====================

    private static void resolveSelect(SQLSelect select, Set<String> outerCte, Accumulator acc) {
        if (select == null) {
            return;
        }
        Set<String> cte = new LinkedHashSet<>(outerCte);
        cte.addAll(collectCteNames(select.getWithSubQuery()));
        if (select.getWithSubQuery() != null) {
            for (SQLWithSubqueryClause.Entry e : select.getWithSubQuery().getEntries()) {
                resolveSelect(e.getSubQuery(), cte, acc);
            }
        }
        resolveQuery(select.getQuery(), cte, acc);
    }

    private static void resolveQuery(SQLSelectQuery query, Set<String> cte, Accumulator acc) {
        if (query instanceof SQLSelectQueryBlock block) {
            SelectScope scope = new SelectScope();
            addFromItem(block.getFrom(), scope, cte, acc);
            ColumnCollector cc = new ColumnCollector(scope, cte, acc);
            for (var item : block.getSelectList()) {
                acceptExpr(item.getExpr(), cc);
            }
            acceptExpr(block.getWhere(), cc);
            if (block.getGroupBy() != null) {
                acceptExpr(block.getGroupBy().getHaving(), cc);
                for (SQLExpr g : block.getGroupBy().getItems()) {
                    acceptExpr(g, cc);
                }
            }
        } else if (query instanceof SQLUnionQuery union) {
            resolveQuery(union.getLeft(), cte, acc);
            resolveQuery(union.getRight(), cte, acc);
        }
    }

    /** WITH 内定义的 CTE 名(小写),引用这些名字的 FROM 项不当真实表。Entry 名即其 alias。 */
    private static Set<String> collectCteNames(SQLWithSubqueryClause with) {
        if (with == null) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<>();
        for (SQLWithSubqueryClause.Entry e : with.getEntries()) {
            if (e.getAlias() != null) {
                names.add(SqlAst.normalize(e.getAlias()).toLowerCase(Locale.ROOT));
            }
            // 嵌套 WITH:CTE 子查询里可能再定义 CTE
            if (e.getSubQuery() != null) {
                names.addAll(collectCteNames(e.getSubQuery().getWithSubQuery()));
            }
        }
        return names;
    }

    /** 处理 FROM 项,递归 JOIN / 子查询 / 真实表;给 scope 添加 alias 映射,JOIN ON 内列引用收集到 scope。 */
    private static void addFromItem(SQLTableSource node, SelectScope scope, Set<String> cte, Accumulator acc) {
        if (node == null) {
            return;
        }
        if (node instanceof SQLJoinTableSource join) {
            addFromItem(join.getLeft(), scope, cte, acc);
            addFromItem(join.getRight(), scope, cte, acc);
            acceptExpr(join.getCondition(), new ColumnCollector(scope, cte, acc));
            return;
        }
        if (node instanceof SQLExprTableSource ets) {
            String table = SqlAst.normalize(ets.getTableName());
            if (table == null || table.isEmpty()) {
                return;
            }
            String aliasKey =
                    (ets.getAlias() != null ? SqlAst.normalize(ets.getAlias()) : table).toLowerCase(Locale.ROOT);
            // 单段名命中 CTE → 不是真实表;CTE 的真实底表已由其子查询解析覆盖
            if (ets.getSchema() == null && ets.getCatalog() == null
                    && cte.contains(table.toLowerCase(Locale.ROOT))) {
                return;
            }
            MutableAccess access = acc.add(
                    SqlAst.normalize(ets.getCatalog()), SqlAst.normalize(ets.getSchema()), table,
                    TableAccess.Action.READ);
            scope.addTable(access, aliasKey);
            return;
        }
        if (node instanceof SQLSubqueryTableSource sub) {
            resolveSelect(sub.getSelect(), cte, acc);
            return;
        }
        if (node instanceof SQLUnionQueryTableSource union) {
            resolveQuery(union.getUnion(), cte, acc);
        }
    }

    /** WHERE/SET 表达式里的子查询表收集 READ(UPDATE/DELETE 主体不构造 scope,只到表级)。 */
    private static void collectSubqueryReads(SQLExpr expr, Accumulator acc) {
        if (expr == null) {
            return;
        }
        SelectScope empty = new SelectScope();
        acceptExpr(expr, new ColumnCollector(empty, Collections.emptySet(), acc));
    }

    private static void acceptExpr(SQLExpr expr, ColumnCollector cc) {
        if (expr != null) {
            expr.accept(cc);
        }
    }

    // ==================== 列引用 visitor ====================

    /**
     * 收集表达式内的列引用,按 scope 归属;遇子查询独立解析其 READ 表,不把子查询的列归属到当前 scope。
     */
    private static final class ColumnCollector extends SQLASTVisitorAdapter {

        private final SelectScope scope;
        private final Set<String> cte;
        private final Accumulator acc;

        ColumnCollector(SelectScope scope, Set<String> cte, Accumulator acc) {
            this.scope = scope;
            this.cte = cte;
            this.acc = acc;
        }

        @Override
        public boolean visit(SQLIdentifierExpr x) {
            scope.routeColumn(null, SqlAst.normalize(x.getName()));
            return false;
        }

        @Override
        public boolean visit(SQLPropertyExpr x) {
            if (SqlAst.STAR.equals(x.getName())) {
                return false;
            }
            scope.routeColumn(SqlAst.ownerAlias(x), SqlAst.normalize(x.getName()));
            return false;
        }

        @Override
        public boolean visit(SQLAllColumnExpr x) {
            return false;
        }

        @Override
        public boolean visit(SQLQueryExpr x) {
            resolveSelect(x.getSubQuery(), cte, acc);
            return false;
        }

        @Override
        public boolean visit(SQLInSubQueryExpr x) {
            acceptExpr(x.getExpr(), this);
            resolveSelect(x.getSubQuery(), cte, acc);
            return false;
        }

        @Override
        public boolean visit(SQLExistsExpr x) {
            resolveSelect(x.getSubQuery(), cte, acc);
            return false;
        }
    }

    // ==================== 表 access 入口 ====================

    /** FROM/目标表 → accumulator(同 cat/db/table/action 合并),返 MutableAccess 句柄供列归属。 */
    private static MutableAccess addTableAccess(SQLTableSource src, TableAccess.Action action, Accumulator acc) {
        if (!(src instanceof SQLExprTableSource ets)) {
            return null;
        }
        String table = SqlAst.normalize(ets.getTableName());
        if (table == null || table.isEmpty()) {
            return null;
        }
        return acc.add(SqlAst.normalize(ets.getCatalog()), SqlAst.normalize(ets.getSchema()), table, action);
    }

    /** INSERT 目标列表 / 列引用直接写到目标表 access。 */
    private static void addExplicitColumns(List<SQLExpr> cols, MutableAccess target) {
        if (cols == null) {
            return;
        }
        for (SQLExpr c : cols) {
            addColumnExpr(c, target);
        }
    }

    private static void addColumnExpr(SQLExpr col, MutableAccess target) {
        String name = SqlAst.lastName(col);
        if (SqlAst.isConcreteColumnName(name)) {
            target.addColumn(name);
        }
    }

    // ==================== Accumulator + 状态对象 ====================

    /** 同 (catalog, database, table, action) 在多次访问间合并 columns。 */
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

        /** 降级到表级:清空已收集列且后续 addColumn 无效。多表作用域出现未限定列时该 access 不再适合列粒度。 */
        void degrade() {
            degraded = true;
            columns.clear();
        }
    }

    /** 单个 SELECT 作用域:alias→access 映射 + 此作用域直接出现的 READ 表集合。 */
    private static final class SelectScope {

        private final Map<String, MutableAccess> aliasToAccess = new LinkedHashMap<>();
        private final List<MutableAccess> directReads = new ArrayList<>();

        void addTable(MutableAccess access, String alias) {
            if (alias != null && !alias.isEmpty()) {
                aliasToAccess.put(alias.toLowerCase(Locale.ROOT), access);
            }
            if (!directReads.contains(access)) {
                directReads.add(access);
            }
        }

        /** 路由一个列引用:限定列按 alias 归属;未限定列单表归属、多表降级。找不到 alias = 子查询/未知,忽略。 */
        void routeColumn(String alias, String col) {
            if (!SqlAst.isConcreteColumnName(col)) {
                return;
            }
            if (alias != null && !alias.isEmpty()) {
                MutableAccess target = aliasToAccess.get(alias.toLowerCase(Locale.ROOT));
                if (target != null) {
                    target.addColumn(col);
                }
                return;
            }
            if (directReads.size() == 1) {
                directReads.get(0).addColumn(col);
            } else if (directReads.size() > 1) {
                for (MutableAccess m : directReads) {
                    m.degrade();
                }
            }
        }
    }
}
