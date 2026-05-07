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

package io.github.zzih.rudder.runtime.aliyun.spark;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aliyun.AliyunRuntimeProperties;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.spark.sql.SparkSqlTask;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.emr_serverless_spark20230808.Client;
import com.aliyun.emr_serverless_spark20230808.models.CreateSqlStatementRequest;
import com.aliyun.emr_serverless_spark20230808.models.CreateSqlStatementResponse;
import com.aliyun.emr_serverless_spark20230808.models.GetSqlStatementRequest;
import com.aliyun.emr_serverless_spark20230808.models.GetSqlStatementResponse;
import com.aliyun.emr_serverless_spark20230808.models.GetSqlStatementResponseBody;
import com.aliyun.teaopenapi.models.OpenApiRequest;
import com.aliyun.teaopenapi.models.Params;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/** 阿里云 Serverless Spark SQL 任务,通过 createSqlStatement 提交 SQL,完成后把 columns/rows 喂回父类 sink。 */
@Slf4j
public class AliyunSparkSqlTask extends SparkSqlTask {

    private static final int POLL_INTERVAL_SECONDS = 1;
    private static final int POLL_MAX_ATTEMPTS = 600;

    private final AliyunRuntimeProperties props;
    private final Client sparkClient;
    private volatile String statementId;

    public AliyunSparkSqlTask(TaskExecutionContext ctx, SqlTaskParams params,
                              AliyunRuntimeProperties props, Client sparkClient) {
        super(ctx, params);
        this.props = props;
        this.sparkClient = sparkClient;
    }

    @Override
    public void init() throws TaskException {
        // 跳过父类 JDBC 连接 — 云端自带 SQL 执行环境
        this.status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        if (resultSink == null) {
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    "ResultSink 未注入 - Worker pipeline 必须注入 sink");
        }

        String wsId = props.getSpark().getWorkspaceId();
        int queryLimit = params.getQueryLimit() > 0 ? params.getQueryLimit() : 1000;

        CreateSqlStatementRequest createReq = new CreateSqlStatementRequest()
                .setCodeContent(params.getSql())
                .setDefaultDatabase("default")
                .setLimit(queryLimit)
                .setSqlComputeId(props.getSpark().getResourceQueueId());

        try {
            log.info("Submitting Spark SQL to Aliyun Serverless Spark...");
            CreateSqlStatementResponse resp = sparkClient.createSqlStatement(wsId, createReq);
            var body = resp.getBody();
            if (body == null || body.getData() == null || body.getData().getStatementId() == null) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "Aliyun Spark SQL: invalid response, no statementId returned");
            }
            this.statementId = body.getData().getStatementId();
            log.info("Spark SQL submitted, statementId={}", statementId);

            GetSqlStatementResponseBody.GetSqlStatementResponseBodyData finalData =
                    pollStatementResult(wsId, statementId);
            writeResult(finalData);
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        } catch (Exception e) {
            this.status = TaskStatus.FAILED;
            log.error("Aliyun Spark SQL failed: {}", e.getMessage(), e);
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    @Override
    public void cancel() throws TaskException {
        this.status = TaskStatus.CANCELLED;
        try {
            if (statementId == null) {
                return;
            }
            String wsId = props.getSpark().getWorkspaceId();
            log.info("Cancelling Aliyun Spark SQL statement: {}", statementId);
            try {
                // SDK 没有封装 cancelSqlStatement,通过 callApi 直接调
                Params apiParams = new Params()
                        .setAction("CancelSqlStatement")
                        .setVersion("2023-08-08")
                        .setProtocol("HTTPS")
                        .setMethod("POST")
                        .setAuthType("AK")
                        .setStyle("ROA")
                        .setPathname("/api/v1/workspaces/" + wsId
                                + "/sqlStatement/" + statementId + "/cancel")
                        .setReqBodyType("json")
                        .setBodyType("json");
                sparkClient.callApi(apiParams, new OpenApiRequest(),
                        new com.aliyun.teautil.models.RuntimeOptions());
            } catch (Exception e) {
                log.warn("Failed to cancel Aliyun Spark SQL statement {}: {}", statementId, e.getMessage());
            }
        } finally {
            closeSinkQuietly();
        }
    }

    /** 兜底关闭 sink:cancel 时 handle() 可能正在写,sink 不关 → 本地 writer / 上传线程泄漏。 */
    private void closeSinkQuietly() {
        if (resultSink == null) {
            return;
        }
        try {
            resultSink.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private GetSqlStatementResponseBody.GetSqlStatementResponseBodyData pollStatementResult(String wsId,
                                                                                            String stmtId) {
        return PollingUtils.poll(
                () -> {
                    try {
                        GetSqlStatementResponse resp = sparkClient.getSqlStatement(
                                wsId, stmtId, new GetSqlStatementRequest());
                        return resp.getBody().getData();
                    } catch (Exception e) {
                        log.warn("Error polling SQL statement: {}", e.getMessage());
                        return null;
                    }
                },
                data -> {
                    if (data == null) {
                        return null;
                    }
                    String state = data.getState();
                    if ("available".equalsIgnoreCase(state)) {
                        return data;
                    }
                    if ("error".equalsIgnoreCase(state) || "cancelled".equalsIgnoreCase(state)) {
                        String errMsg = data.getSqlErrorMessage() != null ? data.getSqlErrorMessage() : state;
                        throw new RuntimeException("Spark SQL failed: " + errMsg);
                    }
                    return null;
                },
                Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                "Spark SQL timed out after " + POLL_MAX_ATTEMPTS + "s for statement " + stmtId);
    }

    private void writeResult(GetSqlStatementResponseBody.GetSqlStatementResponseBodyData data) throws TaskException {
        List<ColumnMeta> columnMetas = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        var sqlOutputs = data.getSqlOutputs();
        if (sqlOutputs != null && !sqlOutputs.isEmpty()) {
            var output = sqlOutputs.get(0);
            String schemaJson = output.getSchema();
            if (schemaJson != null) {
                JsonNode schemaNode = JsonUtils.parseTree(schemaJson);
                JsonNode fields = schemaNode.path("fields");
                if (fields.isArray()) {
                    for (JsonNode field : fields) {
                        columnMetas.add(ColumnMeta.builder()
                                .name(field.path("name").asText())
                                .dataType(field.path("type").asText("STRING"))
                                .build());
                    }
                }
            }
            String rowsJson = output.getRows();
            if (rowsJson != null) {
                JsonNode rowsNode = JsonUtils.parseTree(rowsJson);
                if (rowsNode.isArray()) {
                    for (JsonNode row : rowsNode) {
                        Map<String, Object> rowMap = new LinkedHashMap<>();
                        if (row.isArray()) {
                            for (int i = 0; i < row.size() && i < columnMetas.size(); i++) {
                                rowMap.put(columnMetas.get(i).getName(), row.get(i).asText());
                            }
                        }
                        rows.add(rowMap);
                    }
                }
            }
        }

        log.info("Aliyun Spark SQL result: {} columns, {} rows", columnMetas.size(), rows.size());

        resultSink.init(columnMetas);
        for (Map<String, Object> row : rows) {
            resultSink.write(row);
        }
        try {
            resultSink.close();
        } catch (IOException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to flush result sink: " + e.getMessage());
        }
    }
}
