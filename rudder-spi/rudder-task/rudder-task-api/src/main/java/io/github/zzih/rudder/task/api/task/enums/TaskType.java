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

package io.github.zzih.rudder.task.api.task.enums;

import static io.github.zzih.rudder.task.api.task.enums.ExecutionMode.BATCH;
import static io.github.zzih.rudder.task.api.task.enums.ExecutionMode.STREAMING;
import static io.github.zzih.rudder.task.api.task.enums.TaskCategory.*;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskType {

    // SQL 任务
    HIVE_SQL("Hive SQL", ".sql", SQL, true, "HIVE", List.of(BATCH)),
    STARROCKS_SQL("StarRocks SQL", ".sql", SQL, true, "STARROCKS", List.of(BATCH)),
    MYSQL("MySQL", ".sql", SQL, true, "MYSQL", List.of(BATCH)),
    DORIS_SQL("Doris SQL", ".sql", SQL, true, "DORIS", List.of(BATCH)),
    POSTGRES_SQL("PostgreSQL", ".sql", SQL, true, "POSTGRES", List.of(BATCH)),
    CLICKHOUSE_SQL("ClickHouse SQL", ".sql", SQL, true, "CLICKHOUSE", List.of(BATCH)),
    TRINO_SQL("Trino SQL", ".sql", SQL, true, "TRINO", List.of(BATCH)),
    SPARK_SQL("Spark SQL", ".sql", SQL, true, "SPARK", List.of(BATCH)),
    FLINK_SQL("Flink SQL", ".sql", SQL, true, "FLINK", List.of(BATCH, STREAMING)),

    // JAR 任务
    SPARK_JAR("Spark JAR", ".json", JAR, false, null, List.of(BATCH)),
    FLINK_JAR("Flink JAR", ".json", JAR, false, null, List.of(BATCH, STREAMING)),

    // 脚本任务
    PYTHON("Python", ".py", SCRIPT, false, null, List.of(BATCH)),
    SHELL("Shell", ".sh", SCRIPT, false, null, List.of(BATCH)),
    HTTP("HTTP", ".json", API, false, null, List.of(BATCH)),

    // 数据集成任务
    SEATUNNEL("SeaTunnel", ".conf", DATA_INTEGRATION, false, null, List.of(BATCH)),

    // 控制流任务（在 master 上执行，不分发到 worker）
    CONDITION("Condition", "", CONTROL, false, null, List.of(BATCH)),
    SUB_WORKFLOW("Sub WorkflowDefinition", "", CONTROL, false, null, List.of(BATCH)),
    SWITCH("Switch", "", CONTROL, false, null, List.of(BATCH)),
    DEPENDENT("Dependent", "", CONTROL, false, null, List.of(BATCH));

    private final String label;
    private final String ext;
    private final TaskCategory category;
    private final boolean needsDatasource;
    private final String datasourceType;
    private final List<ExecutionMode> executionModes;

    /**
     * 如果该任务类型表示控制流节点而非任务节点，则返回 {@code true}。
     */
    public boolean isControlFlow() {
        return category == CONTROL;
    }

    /**
     * SQL 类任务:Worker 端不字符串替换 {@code ${var}} — 留给 SqlExecutor 走 PreparedStatement,
     * 用 {@code ?} + setXxx 防注入。Shell/Python/Jar 等仍走字符串替换。
     */
    public boolean isSql() {
        return category == SQL;
    }

    /**
     * Jackson 序列化固定走 {@code name()}，不受任何 {@code WRITE_ENUMS_USING_INDEX} / 定制 ObjectMapper
     * 配置影响。防御性保证：enum 顺序调整不会破坏 JSON / RPC 兼容。
     */
    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static TaskType fromJson(String value) {
        return value == null ? null : TaskType.valueOf(value);
    }
}
