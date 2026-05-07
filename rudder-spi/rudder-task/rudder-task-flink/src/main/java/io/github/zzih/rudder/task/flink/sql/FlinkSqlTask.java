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

package io.github.zzih.rudder.task.flink.sql;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.AbstractJdbcSqlTask;
import io.github.zzih.rudder.task.api.task.JobTask;
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.api.task.executor.SqlExecutor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Flink SQL 任务。通过 JDBC 连接 Flink SQL Gateway。
 * <ul>
 *   <li>Batch 模式:走 {@link AbstractJdbcSqlTask} 模板,跟普通 SQL 任务一致</li>
 *   <li>Streaming 模式:不走主 SQL 模板,逐条 execute,从 ResultSet 第一行抓 jobId,
 *       isStreaming=true 让 Worker 把 instance 标 RUNNING</li>
 * </ul>
 * engineParams 翻译成 SET 语句在主 SQL 前执行。
 */
@Slf4j
public class FlinkSqlTask extends AbstractJdbcSqlTask implements JobTask {

    protected volatile String flinkJobId;
    protected volatile String trackingUrl;

    public FlinkSqlTask(TaskExecutionContext ctx, SqlTaskParams params) {
        super(ctx, params);
    }

    @Override
    protected SqlDialect dialect() {
        return SqlDialect.FLINK;
    }

    @Override
    public boolean isStreaming() {
        return ExecutionMode.STREAMING.name().equalsIgnoreCase(params.getExecutionMode());
    }

    @Override
    public String getAppId() {
        return flinkJobId;
    }

    @Override
    public String getTrackingUrl() {
        return trackingUrl;
    }

    @Override
    public void handle() throws TaskException {
        if (!isStreaming()) {
            super.handle();
            return;
        }

        // Streaming 路径:不读结果,只抓 jobId(SELECT JOB_ID 风格)
        try {
            try (Statement setupStmt = connection.createStatement()) {
                applyEngineParams(setupStmt);
                applyStreamingMode(setupStmt);
            }

            List<String> stmts = SqlExecutor.splitSqlToList(params.getSql());
            for (int i = 0; i < stmts.size(); i++) {
                String sql = stmts.get(i);
                log.info("Executing streaming ({}/{}): {}", i + 1, stmts.size(),
                        sql.length() <= 200 ? sql : sql.substring(0, 200) + "...");
                try (Statement stmt = connection.createStatement()) {
                    boolean hasRs = stmt.execute(sql);
                    if (hasRs) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            if (rs.next()) {
                                flinkJobId = rs.getString(1);
                                log.info("Flink streaming job submitted, jobId={}", flinkJobId);
                            }
                        }
                    }
                }
            }
            this.status = TaskStatus.SUCCESS;
        } catch (SQLException e) {
            this.status = TaskStatus.FAILED;
            log.error("Flink streaming SQL error: {}", e.getMessage());
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        } finally {
            // streaming 模式 connection 提交完就关闭(集群侧 job 仍在运行)
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    protected void beforeMain(Statement stmt) throws SQLException {
        applyStreamingMode(stmt);
        applyEngineParams(stmt);
    }

    private void applyStreamingMode(Statement stmt) throws SQLException {
        String mode = isStreaming()
                ? ExecutionMode.STREAMING.name().toLowerCase()
                : ExecutionMode.BATCH.name().toLowerCase();
        SqlExecutor.execute(stmt, "SET 'execution.runtime-mode' = '" + mode + "'",
                params.getQueryLimit(), dialect(), null, false, null);
    }

    private void applyEngineParams(Statement stmt) throws SQLException {
        if (params.getEngineParams() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : params.getEngineParams().entrySet()) {
            String setStmt = "SET '" + entry.getKey() + "' = '" + entry.getValue() + "'";
            log.info("SET  → {}", setStmt);
            SqlExecutor.execute(stmt, setStmt, params.getQueryLimit(), dialect(), null, false, null);
        }
    }
}
