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

package io.github.zzih.rudder.task.api.task.executor;

import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.sql.ResolvedColumn;
import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.common.sql.SqlProjectionResolver;
import io.github.zzih.rudder.task.api.task.sink.ResultSink;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所有基于 JDBC 的任务实现共享的 SQL 执行逻辑。
 * 拆分多语句 SQL 并逐条执行，收集最后一条返回结果集的语句的结果。
 * <p>
 * 执行后顺带跑 {@link SqlProjectionResolver} 把结果列追溯回 "原始表.原始列"，
 * 产出的 {@link ColumnMeta} 后续在出口处喂给 {@code RedactionService}，让 alias /
 * 跨源联邦查询也能命中 COLUMN / TAG 规则。
 */
public final class SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);

    private SqlExecutor() {
    }

    /**
     * 在给定的 Connection 上以 PreparedStatement 方式执行 SQL,并把 {@code ${var}} 替换成 {@code ?},
     * {@code !{var}} 原值替换,LIST 类型自动展开成 {@code ?,?,?}。占位符语义和 DolphinScheduler 一致。
     *
     * <p>每条 split 出来的 SQL 都新建一个 PreparedStatement(JDBC 不允许同一份 prepared 跑多条),
     * 跑完即关。最后一条返回结果集的 SQL 通过 {@link ResultSink} 喂出,前面的非查询语句的 ResultSet 丢弃 + warn。
     *
     * @param paramMap prop → Property 视图,SqlPreprocessor 用来识别 {@code ${var}} / {@code !{var}}。
     *                 null 等价空 map(SQL 里没有占位符的纯文本路径)。
     */
    public static void executePrepared(Connection conn, String sqlText, Map<String, Property> paramMap,
                                       int maxRows, int timeoutSeconds, SqlDialect dialect,
                                       String datasourceName, ResultSink sink,
                                       Consumer<Statement> activeRegistrar) throws SQLException {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null for prepared execution");
        }
        String[] statements = splitSql(sqlText);
        boolean sinkInitialized = false;

        for (int si = 0; si < statements.length; si++) {
            String rawSql = statements[si];
            SqlPreprocessor.Prepared prep = SqlPreprocessor.preprocess(rawSql, paramMap);
            String sql = prep.sql();
            if (statements.length > 1) {
                log.info("Executing ({}/{}): {}", si + 1, statements.length,
                        sql.length() <= 200 ? sql : sql.substring(0, 200) + "...");
            }
            log.info("Bind {} param(s)", prep.binds().size());

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // 注册 active statement 给 cancel() 用 — 注意 registrar 必须**包到 drainResultSet 之外**,
                // 否则 ps.execute() 返回后 registrar 提前清掉,而 drainResultSet 还在 streaming 拉行的
                // 那 N 秒/分钟里 cancel() 看到 currentStatement=null 就 silent no-op 了。
                if (activeRegistrar != null) {
                    activeRegistrar.accept(ps);
                }
                try {
                    try {
                        ps.setMaxRows(maxRows);
                    } catch (SQLException e) {
                        log.warn("setMaxRows not supported, skipping: {}", e.getMessage());
                    }
                    if (timeoutSeconds > 0) {
                        try {
                            ps.setQueryTimeout(timeoutSeconds);
                        } catch (SQLException e) {
                            log.warn("setQueryTimeout not supported, skipping: {}", e.getMessage());
                        }
                    }
                    applyStreamingFetch(ps, dialect);
                    SqlParamBinder.bindAll(ps, prep.binds());

                    boolean hasResultSet = ps.execute();
                    if (hasResultSet) {
                        try (ResultSet rs = ps.getResultSet()) {
                            sinkInitialized = drainResultSet(rs, rawSql, dialect, datasourceName, sink);
                        }
                    } else {
                        log.info("{} rows affected", ps.getUpdateCount());
                    }
                } finally {
                    if (activeRegistrar != null) {
                        activeRegistrar.accept(null);
                    }
                }
            }
        }

        // 多条语句全是 update 的场景:sink 仍然需要 init,后续 getRowCount/getColumnMetas 才有合理值
        if (!sinkInitialized) {
            sink.init(List.of());
        }
    }

    /** 拉一个 ResultSet 全量喂给 sink。返回 true 表示已 init 过(避免重复 init)。 */
    private static boolean drainResultSet(ResultSet rs, String sql, SqlDialect dialect,
                                          String datasourceName, ResultSink sink) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        List<String> cols = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            cols.add(meta.getColumnLabel(i));
        }
        // 原始 SQL(替换前)更适合喂 SqlProjectionResolver — 它需要看到 alias 文本
        List<ColumnMeta> columnMetas = buildColumnMetas(cols, sql, dialect, datasourceName);
        sink.init(columnMetas);

        int mapCapacity = (int) Math.ceil(colCount / 0.75);
        long rows = 0;
        while (rs.next()) {
            Map<String, Object> rowMap = new LinkedHashMap<>(mapCapacity);
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                rowMap.put(cols.get(i - 1), val != null ? val.toString() : null);
            }
            sink.write(rowMap);
            rows++;
        }
        log.info("Query returned {} rows", rows);
        return true;
    }

    /**
     * 在给定的 Statement 上执行可能包含多条语句的 SQL 文本。结果通过 {@link ResultSink} 流式喂出。
     *
     * @param dialect SQL 方言。给 {@link SqlProjectionResolver} 选 Calcite Lex,给
     *                {@link #applyStreamingFetch} 选 JDBC fetch 策略。null 表示未知,走默认 lex
     *                + 通用 fetchSize(1000)。
     * @param datasourceName Rudder 数据源名,写进 ColumnMeta 给元数据 tag 解析用。可空。
     * @param expectResultSet {@code true}:要求调用方提供 sink,每行 {@code sink.write} 喂出;
     *                返回结果集前先 {@code sink.init(columnMetas)}。
     *                {@code false}:用于 pre/post/SET 等副作用语句,sink 可为 null;
     *                即使驱动返回 ResultSet 也直接丢弃(产品契约不允许 pre/post 跑查询)。
     * @param sink expectResultSet=true 时必须非空;false 时忽略。
     */
    public static void execute(Statement stmt, String sqlText, int maxRows,
                               SqlDialect dialect, String datasourceName,
                               boolean expectResultSet, ResultSink sink) throws SQLException {
        if (expectResultSet && sink == null) {
            throw new IllegalArgumentException("sink must not be null when expectResultSet=true");
        }
        String[] statements = splitSql(sqlText);

        try {
            stmt.setMaxRows(maxRows);
        } catch (SQLException e) {
            log.warn("setMaxRows not supported, skipping: {}", e.getMessage());
        }

        if (expectResultSet) {
            applyStreamingFetch(stmt, dialect);
        }

        boolean sinkInitialized = false;

        for (int si = 0; si < statements.length; si++) {
            String sql = statements[si];
            if (statements.length > 1) {
                log.info("Executing ({}/{}): {}", si + 1, statements.length,
                        sql.length() <= 200 ? sql : sql.substring(0, 200) + "...");
            }

            boolean hasResultSet = stmt.execute(sql);

            if (!expectResultSet) {
                if (hasResultSet) {
                    log.warn("Non-query statement returned a ResultSet; ignored: {}",
                            sql.length() <= 200 ? sql : sql.substring(0, 200) + "...");
                } else {
                    log.info("{} rows affected", stmt.getUpdateCount());
                }
                continue;
            }

            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    sinkInitialized = drainResultSet(rs, sql, dialect, datasourceName, sink);
                }
            } else {
                log.info("{} rows affected", stmt.getUpdateCount());
            }
        }

        // 多条语句全是 update 的场景:sink 仍然需要 init,后续 getRowCount/getColumnMetas 才有合理值
        if (expectResultSet && !sinkInitialized) {
            sink.init(List.of());
        }
    }

    /**
     * 配置 JDBC 驱动按游标拉行,避免一次把整个结果集 buffer 到客户端内存。
     * 不同 dialect 的开关不一样:
     * <ul>
     *   <li>MySQL / Doris / StarRocks:{@code setFetchSize(Integer.MIN_VALUE)} —— MySQL Connector/J
     *       的行级流式魔法值,与 ResultSet TYPE_FORWARD_ONLY + CONCUR_READ_ONLY 配合工作</li>
     *   <li>POSTGRES:必须 {@code setAutoCommit(false)} + {@code setFetchSize(N)} 才走游标</li>
     *   <li>其他 (Hive / Trino / ClickHouse / Spark / Flink):默认就是流式或按 fetchSize 拉</li>
     * </ul>
     * 失败只记 warn,不阻断执行 —— 大不了退化到一次性拉(但内存就受总行数 / maxRows 影响)。
     */
    private static void applyStreamingFetch(Statement stmt, SqlDialect dialect) {
        try {
            if (dialect == null) {
                stmt.setFetchSize(1000);
                return;
            }
            switch (dialect) {
                case MYSQL, DORIS, STARROCKS -> stmt.setFetchSize(Integer.MIN_VALUE);
                case POSTGRES -> {
                    stmt.getConnection().setAutoCommit(false);
                    stmt.setFetchSize(1000);
                }
                case HIVE, TRINO, CLICKHOUSE, SPARK, FLINK -> stmt.setFetchSize(1000);
            }
        } catch (SQLException e) {
            log.warn("setFetchSize not supported for dialect {}, skipping: {}", dialect, e.getMessage());
        }
    }

    /**
     * 把结果列名 + SQL 喂给投影解析器，按位置对齐生成 ColumnMeta。解析失败的列只保留 name。
     * <p>
     * Flink/Spark 这种远程运行时不走 JDBC ResultSet,从 SDK 拿到结果列名后调本方法补 ColumnMeta。
     */
    public static List<ColumnMeta> buildColumnMetas(List<String> resultColumns, String sql,
                                                    SqlDialect dialect, String datasourceName) {
        List<ResolvedColumn> resolved = SqlProjectionResolver.resolve(sql, dialect);
        List<ColumnMeta> out = new ArrayList<>(resultColumns.size());
        for (int i = 0; i < resultColumns.size(); i++) {
            String name = resultColumns.get(i);
            ColumnMeta.ColumnMetaBuilder b = ColumnMeta.builder()
                    .name(name)
                    .datasourceName(datasourceName);
            if (i < resolved.size()) {
                ResolvedColumn rc = resolved.get(i);
                if (rc.isSimpleRef()) {
                    b.originalTable(rc.getOriginalTable());
                    b.originalColumn(rc.getOriginalColumn());
                }
            }
            out.add(b.build());
        }
        return out;
    }

    /**
     * 按分号拆分 SQL 文本，同时正确处理带引号的字符串。
     */
    public static String[] splitSql(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String stmt = current.toString().strip();
                if (!stmt.isEmpty()) {
                    result.add(stmt);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().strip();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result.toArray(new String[0]);
    }

    /**
     * 将 SQL 文本拆分为单条语句的列表。
     */
    public static List<String> splitSqlToList(String sql) {
        return List.of(splitSql(sql));
    }
}
