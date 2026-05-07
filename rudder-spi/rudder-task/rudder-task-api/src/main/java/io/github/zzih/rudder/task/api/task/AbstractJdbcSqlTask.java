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

package io.github.zzih.rudder.task.api.task;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.jdbc.JdbcConnections;
import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.parser.VarPoolFilter;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.api.task.executor.SqlExecutor;
import io.github.zzih.rudder.task.api.task.executor.SqlPreprocessor;
import io.github.zzih.rudder.task.api.task.sink.ResultSink;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所有基于 JDBC 的 SQL Task 的统一基类。模板方法封装:
 * <ol>
 *   <li>{@link #init()} 建 JDBC 连接</li>
 *   <li>{@link #handle()} 跑 pre → main(流式喂 sink) → post</li>
 *   <li>{@link #cancel()} 取消当前 statement</li>
 *   <li>统一的异常分类 (TIMEOUT / FAILED) 和资源清理</li>
 * </ol>
 *
 * <p>子类只需提供两个东西:
 * <ul>
 *   <li>{@link #dialect()} —— 给 SqlExecutor 的 dialect 提示("MYSQL" / "TRINO" / ...)</li>
 *   <li>可选 {@link #beforeMain(Statement)} —— Hive 用来跑 SET engineParams 之类</li>
 * </ul>
 */
public abstract class AbstractJdbcSqlTask extends AbstractTask implements ResultableTask, DataSourceAwareTask {

    private static final Logger log = LoggerFactory.getLogger(AbstractJdbcSqlTask.class);

    protected final SqlTaskParams params;
    protected DataSourceInfo dataSourceInfo;
    /**
     * 必须由 Worker 的 ResultSinkInjector 注入(生产路径 = StreamingFileResultSink,流式落盘 + 上传 + 脱敏)。
     * task-api 不直接依赖 service-shared,所以这里只声明槽位,不给默认值。
     * handle() 会 fail-fast 检查,防止"忘了注入 → 大结果集静默走内存 → OOM"这条陷阱。
     */
    protected ResultSink resultSink;

    protected volatile TaskStatus status = TaskStatus.SUBMITTED;
    protected volatile Connection connection;
    protected volatile Statement currentStatement;

    protected AbstractJdbcSqlTask(TaskExecutionContext ctx, SqlTaskParams params) {
        super(ctx);
        this.params = params;
    }

    /** 子类声明自身的 SQL 方言,用于 SqlExecutor 列血缘解析 + JDBC 流式 fetch 策略。 */
    protected abstract SqlDialect dialect();

    /** 子类可 override:在主 SQL 之前执行的钩子(Hive 跑 SET engineParams 等)。 */
    protected void beforeMain(Statement stmt) throws SQLException, TaskException {
        // 默认 no-op
    }

    @Override
    public void setDataSourceInfo(DataSourceInfo dsInfo) {
        this.dataSourceInfo = dsInfo;
    }

    @Override
    public void setResultSink(ResultSink sink) {
        this.resultSink = sink;
    }

    @Override
    public void init() throws TaskException {
        DataSourceInfo ds = effectiveDataSourceInfo();
        log.info("Connecting to {} → {}", dialect(), ds.getJdbcUrl());
        Properties props = new Properties();
        if (ds.getUsername() != null) {
            props.setProperty("user", ds.getUsername());
        }
        if (ds.getPassword() != null) {
            props.setProperty("password", ds.getPassword());
        }
        if (ds.getProperties() != null) {
            ds.getProperties().forEach(props::setProperty);
        }
        try {
            this.connection = JdbcConnections.open(ds.getJdbcUrl(), props, ds.getDriverClass());
            this.status = TaskStatus.RUNNING;
        } catch (SQLException e) {
            this.status = TaskStatus.FAILED;
            log.error("{} connection failed: {}", dialect(), e.getMessage());
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    dialect() + " connection failed: " + e.getMessage());
        }
    }

    @Override
    public void handle() throws TaskException {
        if (resultSink == null) {
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    "ResultSink 未注入 - 任务必须由 Worker pipeline 注入流式 sink (StreamingFileResultSink)");
        }
        try {
            runStatements(params.getPreStatements(), "Pre");

            // beforeMain 钩子(Hive SET engineParams 等)仍走旧 Statement 路径 — 不需要 prepared,
            // 不接受用户参数,纯系统语句。这里建一个临时 Statement 给子类用,跑完即关。
            try (var setupStmt = connection.createStatement()) {
                if (ctx.getTimeoutSeconds() > 0) {
                    setupStmt.setQueryTimeout(ctx.getTimeoutSeconds());
                }
                beforeMain(setupStmt);
            }

            // 主语句走 PreparedStatement:${var} → ?,!{var} → 原值,LIST 自动展开。
            // ctx.prepareParams 由 Worker 装填(instanceProps + built-in),null 等价空 map。
            // activeRegistrar 把当前 PreparedStatement 暴露给 cancel():用户取消时调 stmt.cancel()。
            java.util.Map<String, Property> paramMap = SqlPreprocessor.indexByProp(ctx.getPrepareParams());
            SqlExecutor.executePrepared(connection, params.getSql(), paramMap, params.getQueryLimit(),
                    ctx.getTimeoutSeconds(), dialect(), null, resultSink,
                    s -> this.currentStatement = s);

            runStatements(params.getPostStatements(), "Post");

            // sink.close() 触发 buffer flush + writer close + 上传到 FileStorage。必须在 SUCCESS
            // 之前完成,否则 Worker 拿 getResultPath 时 .idx/数据文件还没在 FileStorage 上,
            // Server 侧 fetchResult 会 download 失败 → 前端拿到空结果。
            try {
                resultSink.close();
            } catch (IOException e) {
                this.status = TaskStatus.FAILED;
                log.error("Failed to flush/upload result: {}", e.getMessage());
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "Failed to flush/upload result: " + e.getMessage());
            }

            this.status = TaskStatus.SUCCESS;
        } catch (SQLTimeoutException e) {
            this.status = TaskStatus.TIMEOUT;
            log.error("SQL timeout: {}", e.getMessage());
            throw new TaskException(TaskErrorCode.TASK_TIMEOUT, e.getMessage());
        } catch (SQLException e) {
            this.status = TaskStatus.FAILED;
            log.error("SQL error: {}", e.getMessage());
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        } finally {
            closeQuietly();
        }
    }

    @Override
    public void cancel() throws TaskException {
        log.info("Cancelling {} task", dialect());
        this.status = TaskStatus.CANCELLED;
        try {
            if (currentStatement != null && !currentStatement.isClosed()) {
                currentStatement.cancel();
            }
        } catch (SQLException e) {
            log.error("Failed to cancel {} statement: {}", dialect(), e.getMessage());
            throw new TaskException(TaskErrorCode.TASK_CANCEL_FAILED, e.getMessage());
        }
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    // 这些 getter 由 Worker 在 task 跑完后调用(收集出口元信息)。task 跑完意味着 handle()
    // 走到底,handle() 入口已 fail-fast 校验过 resultSink 非空,所以这里不重复判空。
    @Override
    public List<ColumnMeta> getResultColumnMetas() {
        return resultSink == null ? List.of() : resultSink.getColumnMetas();
    }

    @Override
    public long getRowCount() {
        return resultSink == null ? 0 : resultSink.getRowCount();
    }

    @Override
    public String getResultPath() {
        return resultSink == null ? null : resultSink.getResultPath();
    }

    @Override
    public Map<String, Object> getFirstRow() {
        return resultSink == null ? null : resultSink.getFirstRow();
    }

    /**
     * SQL 任务的 OUT 出口:firstRow 各列按 ctx.outputParamsSpec 过滤产出 varPool。
     * 例:用户声明 OUT [{prop:total, type:INTEGER}],SQL 跑 {@code SELECT count(*) AS total} →
     * firstRow["total"]=42 → varPool=[{prop:total, direct:OUT, type:INTEGER, value:"42"}]。
     * <p>
     * 多行 LIST 聚合(DS 行为)在当前版本未实现:LIST 类型 OUT 只取首行的同名列值,记 TODO。
     * 实际需要 sink 端 hook,follow-up scope。
     */
    @Override
    public List<Property> getVarPool() {
        Map<String, Object> firstRow = getFirstRow();
        return VarPoolFilter.filterByRow(firstRow, ctx.getOutputParamsSpec());
    }

    /** 跑 pre/post 列表,每条独立 Statement,expectResultSet=false 不接受结果集。 */
    private void runStatements(List<String> statements, String tag) throws SQLException {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        for (String sql : statements) {
            log.info("{}  → {}", tag, sql);
            try (Statement stmt = connection.createStatement()) {
                SqlExecutor.execute(stmt, sql, params.getQueryLimit(), dialect(), null, false, null);
            }
        }
    }

    private DataSourceInfo effectiveDataSourceInfo() throws TaskException {
        DataSourceInfo ds = dataSourceInfo != null ? dataSourceInfo : ctx.getDataSourceInfo();
        if (ds == null) {
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    "No DataSourceInfo for " + dialect() + " task");
        }
        return ds;
    }

    private void closeQuietly() {
        try {
            if (currentStatement != null && !currentStatement.isClosed()) {
                currentStatement.close();
            }
        } catch (SQLException ignored) {
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
        // 兜底关 sink:成功路径已经在 try 末尾显式 close 过(idempotent,这里 no-op);
        // 失败路径靠这里释放本地 writer / idx 文件句柄,避免 leak。
        if (resultSink != null) {
            try {
                resultSink.close();
            } catch (IOException ignored) {
            }
        }
    }
}
