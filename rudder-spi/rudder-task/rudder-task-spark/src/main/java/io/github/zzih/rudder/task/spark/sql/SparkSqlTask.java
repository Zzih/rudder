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

package io.github.zzih.rudder.task.spark.sql;

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.AbstractJdbcSqlTask;
import io.github.zzih.rudder.task.api.task.executor.SqlExecutor;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Spark SQL 任务。通过 JDBC 连接 Spark Thrift Server,跟普通 JDBC SQL Task 走一套模板。
 * engineParams 翻译成 SET 语句在主 SQL 前执行。
 */
@Slf4j
public class SparkSqlTask extends AbstractJdbcSqlTask {

    public SparkSqlTask(TaskExecutionContext ctx, SqlTaskParams params) {
        super(ctx, params);
    }

    @Override
    protected SqlDialect dialect() {
        return SqlDialect.SPARK;
    }

    @Override
    protected void beforeMain(Statement stmt) throws SQLException {
        if (params.getEngineParams() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : params.getEngineParams().entrySet()) {
            String setStmt = "SET " + entry.getKey() + "=" + entry.getValue();
            log.info("SET  → {}", setStmt);
            SqlExecutor.execute(stmt, setStmt, params.getQueryLimit(), dialect(), null, false, null);
        }
    }
}
