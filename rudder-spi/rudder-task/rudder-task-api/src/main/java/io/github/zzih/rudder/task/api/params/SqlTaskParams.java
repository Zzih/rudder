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

package io.github.zzih.rudder.task.api.params;

import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SqlTaskParams extends AbstractTaskParams {

    private Long dataSourceId;
    private String sql;
    private SqlType sqlType;
    private String executionMode;
    /** Worker 在执行前从 RESULT SPI 的 providerParams.defaultQueryRows 覆盖,Script JSON 里的值不生效。 */
    private int queryLimit;
    private List<String> preStatements;
    private List<String> postStatements;
    private Map<String, String> engineParams;

    /**
     * 根据 SQL 内容检测 SQL 类型。
     * SELECT/SHOW/DESCRIBE/EXPLAIN/WITH 为查询语句；其余为非查询语句。
     */
    public static SqlType detectSqlType(String sql) {
        if (sql == null) {
            return SqlType.NON_QUERY;
        }
        String trimmed = sql.strip().toUpperCase();
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW")
                || trimmed.startsWith("DESCRIBE") || trimmed.startsWith("DESC ")
                || trimmed.startsWith("EXPLAIN") || trimmed.startsWith("WITH")) {
            return SqlType.QUERY;
        }
        return SqlType.NON_QUERY;
    }

    @Override
    public boolean validate() {
        return sql != null && !sql.isBlank() && dataSourceId != null;
    }

    @Override
    public TaskType getTaskType() {
        return null; // 由具体的 SQL 任务类型决定
    }
}
