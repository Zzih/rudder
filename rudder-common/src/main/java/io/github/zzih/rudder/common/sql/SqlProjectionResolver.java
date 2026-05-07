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

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.parser.SqlParser;

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
 * 解析失败(语法错误 / 不支持的语法)返回空 list,调用方按"未追溯到"处理即可。
 * <p>
 * 调用方按字符串传 dialect(对齐 {@code DataSourceInfo.type} 的取值,如 "MYSQL"/"TRINO"),
 * 解析器内部映射到 Calcite Lex。未识别的 dialect 走默认 MYSQL lex。
 */
@Slf4j
public final class SqlProjectionResolver {

    private SqlProjectionResolver() {
    }

    /** 解析 SQL,返回投影列列表。解析失败返回空 list(不抛)。null/未知 dialect 走默认 MySQL lex。 */
    public static List<ResolvedColumn> resolve(String sql, SqlDialect dialect) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }
        // parseQuery 不接受末尾分号(当成多语句脚本会抛)。脚本里写 ";" 是常态,直接剥掉。
        String trimmed = sql.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        try {
            SqlParser parser = SqlParser.create(trimmed, parserConfig(dialect));
            SqlNode root = parser.parseQuery();
            return resolveQuery(root, Collections.emptyMap());
        } catch (Exception e) {
            log.debug("SQL projection resolve failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static SqlParser.Config parserConfig(SqlDialect dialect) {
        Lex lex = dialect != null ? dialect.lex() : Lex.MYSQL;
        return SqlParser.config().withLex(lex).withQuoting(lex.quoting).withCaseSensitive(false);
    }

    // ==================== 递归解析 ====================

    private static List<ResolvedColumn> resolveQuery(SqlNode query, Map<String, List<ResolvedColumn>> cte) {
        if (query == null) {
            return Collections.emptyList();
        }
        if (query instanceof SqlOrderBy ob) {
            return resolveQuery(ob.query, cte);
        }
        if (query instanceof SqlWith with) {
            return resolveWith(with, cte);
        }
        if (query instanceof SqlSelect sel) {
            return resolveSelect(sel, cte);
        }
        if (query instanceof SqlCall call && isSetOp(call.getKind())) {
            return resolveSetOp(call, cte);
        }
        return Collections.emptyList();
    }

    private static List<ResolvedColumn> resolveWith(SqlWith with, Map<String, List<ResolvedColumn>> outerCte) {
        Map<String, List<ResolvedColumn>> scope = new LinkedHashMap<>(outerCte);
        for (SqlNode item : with.withList) {
            if (!(item instanceof SqlWithItem wi)) {
                continue;
            }
            String name = unqualifiedName(wi.name);
            List<ResolvedColumn> body = resolveQuery(wi.query, scope);
            // WITH u(x, y) AS (...) 显式列名
            if (wi.columnList != null && !wi.columnList.isEmpty()) {
                List<ResolvedColumn> aliased = new ArrayList<>(body.size());
                for (int i = 0; i < body.size(); i++) {
                    ResolvedColumn rc = cloneCol(body.get(i));
                    if (i < wi.columnList.size()) {
                        rc.setResultName(unqualifiedName(wi.columnList.get(i)));
                    }
                    aliased.add(rc);
                }
                body = aliased;
            }
            scope.put(name.toLowerCase(Locale.ROOT), body);
        }
        return resolveQuery(with.body, scope);
    }

    private static List<ResolvedColumn> resolveSetOp(SqlCall setOp, Map<String, List<ResolvedColumn>> cte) {
        List<List<ResolvedColumn>> arms = new ArrayList<>();
        for (SqlNode operand : setOp.getOperandList()) {
            arms.add(resolveQuery(operand, cte));
        }
        if (arms.isEmpty()) {
            return Collections.emptyList();
        }
        // 以第一个 arm 的列名为准;每列把其他 arm 同位置的 sources 合并进 derivedFrom
        List<ResolvedColumn> first = arms.get(0);
        List<ResolvedColumn> out = new ArrayList<>(first.size());
        for (int i = 0; i < first.size(); i++) {
            ResolvedColumn rc = cloneCol(first.get(i));
            for (int a = 1; a < arms.size(); a++) {
                List<ResolvedColumn> arm = arms.get(a);
                if (i >= arm.size()) {
                    continue;
                }
                ResolvedColumn other = arm.get(i);
                // 若任一 arm 在同列位置不是简单引用,整列退化为派生
                if (!sameSimpleRef(rc, other)) {
                    mergeIntoDerived(rc, other);
                }
            }
            out.add(rc);
        }
        return out;
    }

    private static List<ResolvedColumn> resolveSelect(SqlSelect sel, Map<String, List<ResolvedColumn>> cte) {
        FromScope scope = buildFromScope(sel.getFrom(), cte);
        List<ResolvedColumn> out = new ArrayList<>();
        SqlNodeList list = sel.getSelectList();
        if (list == null) {
            return out;
        }
        for (SqlNode item : list) {
            out.addAll(resolveSelectItem(item, scope));
        }
        return out;
    }

    private static List<ResolvedColumn> resolveSelectItem(SqlNode item, FromScope scope) {
        String alias = null;
        SqlNode expr = item;
        if (item instanceof SqlBasicCall call && call.getKind() == SqlKind.AS) {
            alias = unqualifiedName(call.operand(1));
            expr = call.operand(0);
        }
        // 星号展开
        if (expr instanceof SqlIdentifier id && id.isStar()) {
            if (id.names.size() == 1) {
                // * 全部
                List<ResolvedColumn> all = new ArrayList<>();
                for (List<ResolvedColumn> t : scope.byAlias.values()) {
                    all.addAll(t);
                }
                return all;
            }
            // t.* 指定表
            String tableAlias = id.names.get(id.names.size() - 2).toLowerCase(Locale.ROOT);
            List<ResolvedColumn> tcols = scope.byAlias.get(tableAlias);
            return tcols == null ? Collections.emptyList() : new ArrayList<>(tcols);
        }
        // 简单标识符
        if (expr instanceof SqlIdentifier id) {
            ResolvedColumn rc = scope.resolveIdentifier(id);
            if (rc == null) {
                rc = ResolvedColumn.simple(alias != null ? alias : simpleName(id), null, simpleName(id));
            } else if (alias != null) {
                rc = cloneCol(rc);
                rc.setResultName(alias);
            }
            return List.of(rc);
        }
        // 表达式 / 函数 / 聚合
        List<SourceRef> sources = new ArrayList<>();
        boolean[] isAgg = {false};
        collectColumnRefs(expr, scope, sources, isAgg);
        String name = alias != null ? alias : "__expr";
        return List.of(ResolvedColumn.derived(name, sources, isAgg[0]));
    }

    // ==================== FROM 作用域构建 ====================

    /** 构建 FROM 子树的列可见性。byAlias 保序(star 展开时用到)。 */
    private static FromScope buildFromScope(SqlNode from, Map<String, List<ResolvedColumn>> cte) {
        FromScope s = new FromScope();
        if (from == null) {
            return s;
        }
        addFromItem(s, from, cte);
        return s;
    }

    private static void addFromItem(FromScope s, SqlNode node, Map<String, List<ResolvedColumn>> cte) {
        if (node instanceof SqlJoin join) {
            addFromItem(s, join.getLeft(), cte);
            addFromItem(s, join.getRight(), cte);
            return;
        }
        // 处理 AS
        String alias = null;
        SqlNode source = node;
        if (node instanceof SqlBasicCall call && call.getKind() == SqlKind.AS) {
            alias = unqualifiedName(call.operand(1));
            source = call.operand(0);
        }
        // 表引用 / CTE 引用
        if (source instanceof SqlIdentifier id) {
            String tableName = simpleName(id);
            List<ResolvedColumn> cols;
            List<ResolvedColumn> cteCols = cte.get(tableName.toLowerCase(Locale.ROOT));
            if (cteCols != null) {
                cols = cloneList(cteCols);
                // 用 CTE 的 resultName 作为可引用列名
            } else {
                // 真实表;列未知,这里先挂个 unknown 标记
                cols = Collections.emptyList();
            }
            String effectiveAlias = (alias != null ? alias : tableName).toLowerCase(Locale.ROOT);
            s.byAlias.put(effectiveAlias, cols);
            // 真实表存一份 originalTable 提示(供 * 解析填充)
            if (cteCols == null) {
                s.realTableByAlias.put(effectiveAlias, tableName);
            }
            return;
        }
        // 子查询(SELECT / WITH / SetOp)
        List<ResolvedColumn> sub = resolveQuery(source, cte);
        String effective = alias != null ? alias.toLowerCase(Locale.ROOT) : "__sub" + s.byAlias.size();
        s.byAlias.put(effective, sub);
    }

    // ==================== 辅助 ====================

    private static boolean isSetOp(SqlKind k) {
        return k == SqlKind.UNION || k == SqlKind.INTERSECT || k == SqlKind.EXCEPT;
    }

    private static String simpleName(SqlNode n) {
        if (n instanceof SqlIdentifier id) {
            return id.names.get(id.names.size() - 1);
        }
        return n == null ? null : n.toString();
    }

    private static String unqualifiedName(SqlNode n) {
        if (n instanceof SqlIdentifier id) {
            return id.getSimple();
        }
        return n == null ? null : n.toString();
    }

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
        return java.util.Objects.equals(a.getOriginalTable(), b.getOriginalTable())
                && java.util.Objects.equals(a.getOriginalColumn(), b.getOriginalColumn());
    }

    private static void mergeIntoDerived(ResolvedColumn target, ResolvedColumn other) {
        if (other.isSimpleRef()) {
            target.getDerivedFrom().add(new SourceRef(null, other.getOriginalTable(), other.getOriginalColumn()));
        } else {
            target.getDerivedFrom().addAll(other.getDerivedFrom());
        }
        if (target.isSimpleRef()) {
            // 原是简单列,现在要退化为派生 —— 把自身也塞进 derivedFrom
            target.getDerivedFrom().add(0, new SourceRef(null, target.getOriginalTable(), target.getOriginalColumn()));
            target.setOriginalTable(null);
            target.setOriginalColumn(null);
        }
    }

    /** 常见聚合函数名。纯解析阶段(未 validate)operator.isAggregator() 不可靠,靠名字兜。 */
    private static final java.util.Set<String> AGGREGATE_FUNCTIONS = java.util.Set.of(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "STDDEV", "VARIANCE", "STDDEV_POP", "STDDEV_SAMP",
            "VAR_POP", "VAR_SAMP", "ANY_VALUE", "FIRST_VALUE", "LAST_VALUE", "LISTAGG", "ARRAY_AGG",
            "GROUP_CONCAT", "STRING_AGG", "PERCENTILE_CONT", "PERCENTILE_DISC", "APPROX_COUNT_DISTINCT",
            "APPROX_DISTINCT", "BIT_AND", "BIT_OR", "BIT_XOR", "CORR", "COVAR_POP", "COVAR_SAMP");

    /** 遍历表达式收集所有列引用,顺便标注是否含聚合。 */
    private static void collectColumnRefs(SqlNode expr, FromScope scope, List<SourceRef> out, boolean[] isAgg) {
        if (expr == null) {
            return;
        }
        if (expr instanceof SqlIdentifier id) {
            if (id.isStar()) {
                return;
            }
            ResolvedColumn rc = scope.resolveIdentifier(id);
            if (rc != null) {
                if (rc.isSimpleRef()) {
                    out.add(new SourceRef(null, rc.getOriginalTable(), rc.getOriginalColumn()));
                } else {
                    out.addAll(rc.getDerivedFrom());
                }
            } else {
                out.add(new SourceRef(null, null, simpleName(id)));
            }
            return;
        }
        if (expr instanceof SqlCall call) {
            if (call.getOperator() != null) {
                if (call.getOperator().isAggregator()
                        || AGGREGATE_FUNCTIONS.contains(call.getOperator().getName().toUpperCase(Locale.ROOT))) {
                    isAgg[0] = true;
                }
            }
            for (SqlNode operand : call.getOperandList()) {
                collectColumnRefs(operand, scope, out, isAgg);
            }
        }
        // 字面量等不含列引用
    }

    /** FROM 后各个表/子查询提供的列的并集,带表别名维度。 */
    private static class FromScope {

        /** 别名(小写)→ 该别名暴露的列列表(按 select 顺序)。 */
        Map<String, List<ResolvedColumn>> byAlias = new LinkedHashMap<>();
        /** 真实表(非 CTE、非子查询)的别名 → 原始表名。 */
        Map<String, String> realTableByAlias = new LinkedHashMap<>();

        /** 按标识符查:支持 `col` / `t.col`。返回 null 表示 scope 里没 match(可能 col 来自未追溯的真实表)。 */
        ResolvedColumn resolveIdentifier(SqlIdentifier id) {
            if (id.names.size() == 1) {
                // 不带前缀;遍历所有别名找 resultName 匹配
                String col = id.names.get(0);
                for (var entry : byAlias.entrySet()) {
                    for (ResolvedColumn rc : entry.getValue()) {
                        if (equalsIgnoreCase(rc.getResultName(), col)) {
                            return rc;
                        }
                    }
                }
                // 若不在任一子查询/CTE 的 scope,但有真实表 —— 拼 "table.col" 返回简单引用
                if (realTableByAlias.size() == 1) {
                    String tbl = realTableByAlias.values().iterator().next();
                    return ResolvedColumn.simple(col, tbl, col);
                }
                // 多表 join 且列名不带前缀,返回 column-only simple ref
                return ResolvedColumn.simple(col, null, col);
            }
            // t.col 或 schema.t.col
            String tableAlias = id.names.get(id.names.size() - 2).toLowerCase(Locale.ROOT);
            String col = id.names.get(id.names.size() - 1);
            List<ResolvedColumn> cols = byAlias.get(tableAlias);
            if (cols != null) {
                for (ResolvedColumn rc : cols) {
                    if (equalsIgnoreCase(rc.getResultName(), col)) {
                        return rc;
                    }
                }
            }
            // 未在 scope(真实表且未追溯到具体列)—— 用真实表名兜底
            String realTbl = realTableByAlias.get(tableAlias);
            return ResolvedColumn.simple(col, realTbl != null ? realTbl : tableAlias, col);
        }

        private static boolean equalsIgnoreCase(String a, String b) {
            return a != null && a.equalsIgnoreCase(b);
        }
    }
}
