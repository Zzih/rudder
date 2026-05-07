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

package io.github.zzih.rudder.runtime.aliyun.flink;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aliyun.AliyunRuntimeProperties;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.flink.sql.FlinkSqlTask;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.ververica20220718.Client;
import com.aliyun.ververica20220718.models.Artifact;
import com.aliyun.ververica20220718.models.BriefDeploymentTarget;
import com.aliyun.ververica20220718.models.CreateDeploymentHeaders;
import com.aliyun.ververica20220718.models.CreateDeploymentRequest;
import com.aliyun.ververica20220718.models.CreateDeploymentResponse;
import com.aliyun.ververica20220718.models.Deployment;
import com.aliyun.ververica20220718.models.SqlArtifact;

import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 VVP Flink SQL 任务,通过 createDeployment 提交。
 * VVP Flink SQL 通常是 INSERT INTO,不直接返回结果集,sink 按 SQL 任务契约写空数组占位。
 */
@Slf4j
public class AliyunFlinkSqlTask extends FlinkSqlTask {

    private static final int POLL_INTERVAL_SECONDS = 2;
    private static final int POLL_MAX_ATTEMPTS = 600;

    private final AliyunRuntimeProperties props;
    private final Client vvpClient;

    public AliyunFlinkSqlTask(TaskExecutionContext ctx, SqlTaskParams params,
                              AliyunRuntimeProperties props, Client vvpClient) {
        super(ctx, params);
        this.props = props;
        this.vvpClient = vvpClient;
    }

    @Override
    public void init() throws TaskException {
        // VVP 自带 SQL Gateway,不需要本地 JDBC 连接
        this.status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        if (resultSink == null) {
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    "ResultSink 未注入 - Worker pipeline 必须注入 sink");
        }

        boolean streaming = isStreaming();
        String deploymentId = createDeployment(streaming);
        this.flinkJobId = deploymentId;
        log.info("VVP Flink {} SQL deployment created, id={}", streaming ? "streaming" : "batch", deploymentId);

        try {
            if (!streaming) {
                pollBatchUntilTerminal(deploymentId);
            }
            // 不论流批,都要走 sink 关闭(空数组占位,符合 SQL Task 契约)
            resultSink.init(List.of());
            resultSink.close();
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        } catch (IOException e) {
            this.status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to flush result sink: " + e.getMessage());
        }
    }

    @Override
    public void cancel() throws TaskException {
        this.status = TaskStatus.CANCELLED;
        try {
            if (flinkJobId == null) {
                return;
            }
            log.info("Cancelling Aliyun VVP SQL deployment: {}", flinkJobId);
            VvpSavepointUtils.cancelDeployment(props, vvpClient, flinkJobId);
        } finally {
            closeSinkQuietly();
        }
    }

    /** 兜底关闭 sink:cancel 时 handle() 可能正在写,sink 不关会泄漏 writer / 上传线程。 */
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

    private String createDeployment(boolean streaming) throws TaskException {
        String ns = props.getFlink().getNamespace();
        String appName = "rudder-flink-sql-" + System.currentTimeMillis();

        SqlArtifact sqlArtifact = new SqlArtifact().setSqlScript(params.getSql());
        Artifact artifact = new Artifact().setKind("SQLSCRIPT").setSqlArtifact(sqlArtifact);

        Map<String, Object> flinkConf = new HashMap<>();
        flinkConf.put("execution.runtime-mode", streaming ? "streaming" : "batch");
        if (params.getEngineParams() != null) {
            flinkConf.putAll(params.getEngineParams());
        }

        BriefDeploymentTarget target = new BriefDeploymentTarget()
                .setMode("PER_JOB")
                .setName("default-queue");

        Deployment deployment = new Deployment()
                .setName(appName)
                .setArtifact(artifact)
                .setFlinkConf(flinkConf)
                .setDeploymentTarget(target)
                .setExecutionMode(streaming ? ExecutionMode.STREAMING.name() : ExecutionMode.BATCH.name());

        CreateDeploymentRequest createReq = new CreateDeploymentRequest().setBody(deployment);
        CreateDeploymentHeaders headers = new CreateDeploymentHeaders()
                .setWorkspace(props.getFlink().getWorkspaceId());

        try {
            log.info("Creating VVP Flink SQL deployment (mode={})...", streaming ? "streaming" : "batch");
            CreateDeploymentResponse resp = vvpClient.createDeploymentWithOptions(
                    ns, createReq, headers, new com.aliyun.teautil.models.RuntimeOptions());
            var body = resp.getBody();
            if (body == null || body.getData() == null || body.getData().getDeploymentId() == null) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "VVP Flink SQL: invalid response, no deploymentId returned");
            }
            return body.getData().getDeploymentId();
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to create Flink SQL deployment: " + e.getMessage());
        }
    }

    private void pollBatchUntilTerminal(String deploymentId) throws TaskException {
        try {
            PollingUtils.poll(
                    () -> VvpSavepointUtils.queryCurrentJobStatus(props, vvpClient, deploymentId),
                    state -> {
                        if (state == null) {
                            return null;
                        }
                        return switch (state.toUpperCase()) {
                            case "FINISHED" -> Boolean.TRUE;
                            case "FAILED" ->
                                throw new RuntimeException("VVP Flink SQL deployment failed: " + deploymentId);
                            case "CANCELLED" ->
                                throw new RuntimeException("VVP Flink SQL deployment cancelled: " + deploymentId);
                            default -> null;
                        };
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "VVP Flink SQL batch deployment timed out: " + deploymentId);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }
}
