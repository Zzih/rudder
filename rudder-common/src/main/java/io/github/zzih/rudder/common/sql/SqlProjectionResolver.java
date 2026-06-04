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

import io.github.zzih.rudder.common.sql.ResolvedColumn.SourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.ast.statement.SQLUnionQueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * SQL 投影解析器 —— 把结果集每列追溯回 "原始表.原始列"。
 * <p>
 * 覆盖的语法:
 * <ul>
 *   <li>简单 SELECT、列别名、表别名</li>
 *   <li>子查询(FROM 里嵌 SELECT / UNION / WITH)</li>
 *   <li>CTE(WITH ... AS ...),包括 WITH 里带列名列表</li>
 *   <li>JOIN(INNER/LEFT/RIGHT/FULL/CROSS)</li>
 *   <li>UNION / INTERSECT / EXCEPT(取第一个 arm 的列名,其他 arm 列并入 derivedFrom)</li>
 *   <li>SELECT *(展开 scope 里所有列)</li>
 *   <li>计算列 / 函数 / 聚合(收集涉及的原始列到 derivedFrom)</li>
 * </ul>
 * <p>
 * 解析失败(语法错误 / 非 query)返回空 list,调用方按"未追溯到"处理即可。
 * <p>
 * null / 未知 dialect 走默认 MySQL 方言。
 */
@Slf4j
public final class SqlProjectionResolver {

    private SqlProjectionResolver() {
    }

    /** 解析 SQL,返回投影列列表。解析失败返回空 list(不抛)。null/未知 dialect 走默认 MySQL。 */
    public static List<ResolvedColumn> resolve(String sql, SqlDialect dialect) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = sql.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        try {
            List<SQLStatement> stmts = DruidSqlParser.parse(trimmed, dialect);
            if (stmts.isEmpty() || !(stmts.get(0) instanceof SQLSelectStatement sel)) {
                return Collections.emptyList();
            }
            return resolveSelect(sel.getSelect(), Collections.emptyMap());
        } catch (Exception e) {
            log.debug("SQL projection resolve failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 递归解析 ====================

    private static List<ResolvedColumn> resolveSelect(SQLSelect select, Map<String, List<ResolvedColumn>> outerCte) {
        if (select == null) {
            return Collections.emptyList();
        }
        Map<String, List<ResolvedColumn>> cte = new LinkedHashMap<>(outerCte);
        SQLWithSubqueryClause with = select.getWithSubQuery();
        if (with != null) {
            for (SQLWithSubqueryClause.Entry e : with.getEntries()) {
                List<ResolvedColumn> body = resolveSelect(e.getSubQuery(), cte);
                body = applyColumnList(body, e.getColumns());
                if (e.getAlias() != null) {
                    cte.put(SqlAst.normalize(e.getAlias()).toLowerCase(Locale.ROOT), body);
                }
            }
        }
        return resolveQuery(select.getQuery(), cte);
    }

    /** WITH u(x, y) AS (...) 显式列名:按位置重命名 body 的 resultName。 */
    private static List<ResolvedColumn> applyColumnList(List<ResolvedColumn> body, List<SQLName> columnList) {
        if (columnList == null || columnList.isEmpty()) {
            return body;
        }
        List<ResolvedColumn> out = new ArrayList<>(body.size());
        for (int i = 0; i < body.size(); i++) {
            ResolvedColumn rc = cloneCol(body.get(i));
            if (i < columnList.size()) {
                rc.setResultName(SqlAst.normalize(columnList.get(i).getSimpleName()));
            }
            out.add(rc);
        }
        return out;
    }

    private static List<ResolvedColumn> resolveQuery(SQLSelectQuery query, Map<String, List<ResolvedColumn>> cte) {
        if (query instanceof SQLSelectQueryBlock block) {
            return resolveBlock(block, cte);
        }
        if (query instanceof SQLUnionQuery union) {
            return resolveSetOp(union, cte);
        }
        return Collections.emptyList();
    }

    private static List<ResolvedColumn> resolveSetOp(SQLUnionQuery union, Map<String, List<ResolvedColumn>> cte) {
        List<ResolvedColumn> left = resolveQuery(union.getLeft(), cte);
        List<ResolvedColumn> right = resolveQuery(union.getRight(), cte);
        // 以 left(可能本身是嵌套 union 的合并结果)的列名为准,每列把 right 同位置 sources 合并进 derivedFrom
        List<ResolvedColumn> out = new ArrayList<>(left.size());
        for (int i = 0; i < left.size(); i++) {
            ResolvedColumn rc = cloneCol(left.get(i));
            if (i < right.size() && !sameSimpleRef(rc, right.get(i))) {
                mergeIntoDerived(rc, right.get(i));
            }
            out.add(rc);
        }
        return out;
    }

    private static List<ResolvedColumn> resolveBlock(SQLSelectQueryBlock block,
                                                     Map<String, List<ResolvedColumn>> cte) {
        FromScope scope = new FromScope();
        addFromItem(block.getFrom(), scope, cte);
        List<ResolvedColumn> out = new ArrayList<>();
        for (SQLSelectItem item : block.getSelectList()) {
            out.addAll(resolveSelectItem(item, scope));
        }
        return out;
    }

    private static List<ResolvedColumn> resolveSelectItem(SQLSelectItem item, FromScope scope) {
        String alias = item.getAlias() != null ? SqlAst.normalize(item.getAlias()) : null;
        SQLExpr expr = item.getExpr();
        // 星号展开
        if (expr instanceof SQLAllColumnExpr star) {
            return expandStar(star.getOwner(), scope);
        }
        if (expr instanceof SQLPropertyExpr p && SqlAst.STAR.equals(p.getName())) {
            return expandStar(p.getOwner(), scope);
        }
        // 简单列引用
        if (expr instanceof SQLIdentifierExpr || expr instanceof SQLPropertyExpr) {
            ResolvedColumn rc = scope.resolveIdentifier(expr);
            if (rc == null) {
                String col = SqlAst.lastName(expr);
                return List.of(ResolvedColumn.simple(alias != null ? alias : col, null, col));
            }
            if (alias != null) {
                rc = cloneCol(rc);
                rc.setResultName(alias);
            }
            return List.of(rc);
        }
        // 表达式 / 函数 / 聚合
        ColumnRefCollector collector = new ColumnRefCollector(scope);
        expr.accept(collector);
        String name = alias != null ? alias : "__expr";
        return List.of(ResolvedColumn.derived(name, collector.sources, collector.aggregate));
    }

    /** {@code *}(owner==null)展开 scope 全部列;{@code t.*} 展开该别名列。 */
    private static List<ResolvedColumn> expandStar(SQLExpr owner, FromScope scope) {
        if (owner == null) {
            List<ResolvedColumn> all = new ArrayList<>();
            for (List<ResolvedColumn> t : scope.byAlias.values()) {
                all.addAll(t);
            }
            return all;
        }
        String tableAlias = SqlAst.lastName(owner);
        List<ResolvedColumn> cols = tableAlias == null ? null
                : scope.byAlias.get(tableAlias.toLowerCase(Locale.ROOT));
        return cols == null ? Collections.emptyList() : new ArrayList<>(cols);
    }

    // ==================== FROM 作用域构建 ====================

    private static void addFromItem(SQLTableSource node, FromScope s, Map<String, List<ResolvedColumn>> cte) {
        if (node == null) {
            return;
        }
        if (node instanceof SQLJoinTableSource join) {
            addFromItem(join.getLeft(), s, cte);
            addFromItem(join.getRight(), s, cte);
            return;
        }
        if (node instanceof SQLExprTableSource ets) {
            String tableName = SqlAst.normalize(ets.getTableName());
            String effectiveAlias =
                    (ets.getAlias() != null ? SqlAst.normalize(ets.getAlias()) : tableName).toLowerCase(Locale.ROOT);
            List<ResolvedColumn> cteCols = ets.getSchema() == null && ets.getCatalog() == null
                    ? cte.get(tableName == null ? null : tableName.toLowerCase(Locale.ROOT))
                    : null;
            if (cteCols != null) {
                s.byAlias.put(effectiveAlias, cloneList(cteCols));
            } else {
                s.byAlias.put(effectiveAlias, Collections.emptyList());
                s.realTableByAlias.put(effectiveAlias, tableName);
            }
            return;
        }
        if (node instanceof SQLSubqueryTableSource sub) {
            putDerived(s, sub.getAlias(), resolveSelect(sub.getSelect(), cte));
            return;
        }
        if (node instanceof SQLUnionQueryTableSource union) {
            putDerived(s, union.getAlias(), resolveQuery(union.getUnion(), cte));
        }
    }

    /** 子查询 / 派生表入 scope:有 alias 用 alias,否则给个稳定的占位名。 */
    private static void putDerived(FromScope s, String alias, List<ResolvedColumn> cols) {
        String effective = alias != null
                ? SqlAst.normalize(alias).toLowerCase(Locale.ROOT)
                : "__sub" + s.byAlias.size();
        s.byAlias.put(effective, cols);
    }

    // ==================== 列引用收集(计算列 / 聚合) ====================

    /** 遍历表达式收集所有列引用,顺便标注是否含聚合;子查询不下钻。 */
    private static final class ColumnRefCollector extends SQLASTVisitorAdapter {

        private final FromScope scope;
        private final List<SourceRef> sources = new ArrayList<>();
        private boolean aggregate;

        ColumnRefCollector(FromScope scope) {
            this.scope = scope;
        }

        @Override
        public boolean visit(SQLAggregateExpr x) {
            aggregate = true;
            return true;
        }

        @Override
        public boolean visit(SQLIdentifierExpr x) {
            record(x);
            return false;
        }

        @Override
        public boolean visit(SQLPropertyExpr x) {
            if (!SqlAst.STAR.equals(x.getName())) {
                record(x);
            }
            return false;
        }

        @Override
        public boolean visit(SQLAllColumnExpr x) {
            return false;
        }

        private void record(SQLExpr ref) {
            ResolvedColumn rc = scope.resolveIdentifier(ref);
            if (rc == null) {
                sources.add(new SourceRef(null, null, SqlAst.lastName(ref)));
            } else if (rc.isSimpleRef()) {
                sources.add(new SourceRef(null, rc.getOriginalTable(), rc.getOriginalColumn()));
            } else {
                sources.addAll(rc.getDerivedFrom());
            }
        }
    }

    // ==================== 辅助 ====================

    private static ResolvedColumn cloneCol(ResolvedColumn c) {
        return ResolvedColumn.builder()
                .resultName(c.getResultName())
                .originalTable(c.getOriginalTable())
                .originalColumn(c.getOriginalColumn())
                .derivedFrom(new ArrayList<>(c.getDerivedFrom()))
                .aggregate(c.isAggregate())
                .build();
    }

    private static List<ResolvedColumn> cloneList(List<ResolvedColumn> src) {
        List<ResolvedColumn> out = new ArrayList<>(src.size());
        for (ResolvedColumn c : src) {
            out.add(cloneCol(c));
        }
        return out;
    }

    private static boolean sameSimpleRef(ResolvedColumn a, ResolvedColumn b) {
        if (!a.isSimpleRef() || !b.isSimpleRef()) {
            return false;
        }
        return Objects.equals(a.getOriginalTable(), b.getOriginalTable())
                && Objects.equals(a.getOriginalColumn(), b.getOriginalColumn());
    }

    private static void mergeIntoDerived(ResolvedColumn target, ResolvedColumn other) {
        if (other.isSimpleRef()) {
            target.getDerivedFrom().add(new SourceRef(null, other.getOriginalTable(), other.getOriginalColumn()));
        } else {
            target.getDerivedFrom().addAll(other.getDerivedFrom());
        }
        if (target.isSimpleRef()) {
            target.getDerivedFrom().add(0, new SourceRef(null, target.getOriginalTable(), target.getOriginalColumn()));
            target.setOriginalTable(null);
            target.setOriginalColumn(null);
        }
    }

    /** FROM 后各表/子查询提供的列,带表别名维度。 */
    private static final class FromScope {

        /** 别名(小写)→ 该别名暴露的列列表(按 select 顺序)。 */
        final Map<String, List<ResolvedColumn>> byAlias = new LinkedHashMap<>();
        /** 真实表(非 CTE、非子查询)的别名 → 原始表名。 */
        final Map<String, String> realTableByAlias = new LinkedHashMap<>();

        /** 按标识符查:支持 `col` / `t.col`。返回 null 表示 scope 里没 match。 */
        ResolvedColumn resolveIdentifier(SQLExpr expr) {
            if (expr instanceof SQLPropertyExpr p && !SqlAst.STAR.equals(p.getName())) {
                String tableAlias = SqlAst.ownerAlias(p);
                String col = SqlAst.normalize(p.getName());
                if (tableAlias != null) {
                    String key = tableAlias.toLowerCase(Locale.ROOT);
                    List<ResolvedColumn> cols = byAlias.get(key);
                    if (cols != null) {
                        for (ResolvedColumn rc : cols) {
                            if (StringUtils.equalsIgnoreCase(rc.getResultName(), col)) {
                                return rc;
                            }
                        }
                    }
                    String realTbl = realTableByAlias.get(key);
                    return ResolvedColumn.simple(col, realTbl != null ? realTbl : tableAlias, col);
                }
                return ResolvedColumn.simple(col, null, col);
            }
            if (expr instanceof SQLIdentifierExpr id) {
                String col = SqlAst.normalize(id.getName());
                for (var entry : byAlias.entrySet()) {
                    for (ResolvedColumn rc : entry.getValue()) {
                        if (StringUtils.equalsIgnoreCase(rc.getResultName(), col)) {
                            return rc;
                        }
                    }
                }
                if (realTableByAlias.size() == 1) {
                    String tbl = realTableByAlias.values().iterator().next();
                    return ResolvedColumn.simple(col, tbl, col);
                }
                return ResolvedColumn.simple(col, null, col);
            }
            return null;
        }
    }
}
