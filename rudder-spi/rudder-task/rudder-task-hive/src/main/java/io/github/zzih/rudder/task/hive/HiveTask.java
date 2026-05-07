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

package io.github.zzih.rudder.task.hive;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.AbstractJdbcSqlTask;
import io.github.zzih.rudder.task.api.task.executor.SqlExecutor;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HiveTask extends AbstractJdbcSqlTask {

    private static final Pattern SAFE_PARAM_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]*$");

    public HiveTask(TaskExecutionContext ctx, SqlTaskParams params) {
        super(ctx, params);
    }

    @Override
    protected SqlDialect dialect() {
        return SqlDialect.HIVE;
    }

    /** Hive 在主 SQL 之前需要 SET hive.execution.engine=tez 之类的引擎参数。 */
    @Override
    protected void beforeMain(Statement stmt) throws SQLException, TaskException {
        if (params.getEngineParams() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : params.getEngineParams().entrySet()) {
            validateEngineParam(entry.getKey(), entry.getValue());
            String setStmt = "SET " + entry.getKey() + "=" + entry.getValue();
            log.info("SET  → {}", setStmt);
            SqlExecutor.execute(stmt, setStmt, params.getQueryLimit(), dialect(), null, false, null);
        }
    }

    private void validateEngineParam(String key, String value) throws TaskException {
        if (!SAFE_PARAM_PATTERN.matcher(key).matches()) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Invalid engine param key: " + key);
        }
        if (value != null && (value.contains(";") || value.contains("--"))) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Invalid engine param value for key '" + key + "'");
        }
    }
}
