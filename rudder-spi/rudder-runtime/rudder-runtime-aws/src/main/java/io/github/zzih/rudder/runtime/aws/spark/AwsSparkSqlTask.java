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

package io.github.zzih.rudder.runtime.aws.spark;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aws.AwsRuntimeProperties;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.spark.sql.SparkSqlTask;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.emrserverless.EmrServerlessClient;
import software.amazon.awssdk.services.emrserverless.model.CancelJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;
import software.amazon.awssdk.services.emrserverless.model.JobDriver;
import software.amazon.awssdk.services.emrserverless.model.JobRunState;
import software.amazon.awssdk.services.emrserverless.model.SparkSubmit;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

/**
 * AWS EMR Serverless Spark SQL 任务。提交 {@code spark-sql -e} 到 EMR Serverless,轮询完成。
 * EMR Serverless 不直接返回结果集,sink 按 SQL 任务契约写空数组占位
 * (后续可通过 PySpark 包装把结果写 JSON 到 S3,这里再拉回)。
 */
@Slf4j
public class AwsSparkSqlTask extends SparkSqlTask {

    private static final int POLL_INTERVAL_SECONDS = 2;
    private static final int POLL_MAX_ATTEMPTS = 600;

    private final AwsRuntimeProperties props;
    private final EmrServerlessClient emrClient;
    private volatile String jobRunId;

    public AwsSparkSqlTask(TaskExecutionContext ctx, SqlTaskParams params,
                           AwsRuntimeProperties props, EmrServerlessClient emrClient) {
        super(ctx, params);
        this.props = props;
        this.emrClient = emrClient;
    }

    @Override
    public void init() throws TaskException {
        this.status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        if (resultSink == null) {
            throw new TaskException(TaskErrorCode.TASK_INIT_FAILED,
                    "ResultSink 未注入 - Worker pipeline 必须注入 sink");
        }

        String emrAppId = props.getSpark().getApplicationId();

        SparkSubmit sparkSubmit = SparkSubmit.builder()
                .entryPoint("command-runner.jar")
                .entryPointArguments(List.of("spark-sql", "-e", params.getSql()))
                .sparkSubmitParameters("--conf spark.sql.catalogImplementation=hive")
                .build();

        StartJobRunRequest startReq = StartJobRunRequest.builder()
                .applicationId(emrAppId)
                .executionRoleArn(props.getSpark().getExecutionRoleArn())
                .jobDriver(JobDriver.builder().sparkSubmit(sparkSubmit).build())
                .name("rudder-spark-sql-" + System.currentTimeMillis())
                .build();

        try {
            log.info("Submitting Spark SQL to EMR Serverless...");
            StartJobRunResponse resp = emrClient.startJobRun(startReq);
            this.jobRunId = resp.jobRunId();
            log.info("Spark SQL submitted, jobRunId={}", jobRunId);

            pollUntilTerminal(emrAppId, jobRunId);

            // EMR Serverless 不直接返结果集,按 SQL Task 契约写空数组
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
        } catch (Exception e) {
            this.status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to submit Spark SQL: " + e.getMessage());
        }
    }

    @Override
    public void cancel() throws TaskException {
        this.status = TaskStatus.CANCELLED;
        try {
            if (jobRunId == null) {
                return;
            }
            try {
                log.info("Cancelling AWS Spark SQL jobRun: {}", jobRunId);
                emrClient.cancelJobRun(CancelJobRunRequest.builder()
                        .applicationId(props.getSpark().getApplicationId())
                        .jobRunId(jobRunId)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to cancel AWS Spark SQL jobRun {}: {}", jobRunId, e.getMessage());
            }
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

    private void pollUntilTerminal(String emrAppId, String jrId) throws TaskException {
        try {
            PollingUtils.poll(
                    () -> {
                        try {
                            GetJobRunResponse resp = emrClient.getJobRun(GetJobRunRequest.builder()
                                    .applicationId(emrAppId)
                                    .jobRunId(jrId)
                                    .build());
                            return resp.jobRun();
                        } catch (Exception e) {
                            log.warn("Error polling EMR Serverless SQL job: {}", e.getMessage());
                            return null;
                        }
                    },
                    jobRun -> {
                        if (jobRun == null) {
                            return null;
                        }
                        JobRunState state = jobRun.state();
                        if (state == JobRunState.SUCCESS) {
                            return Boolean.TRUE;
                        }
                        if (state == JobRunState.FAILED || state == JobRunState.CANCELLED) {
                            throw new RuntimeException("Spark SQL job " + state + ": " + jobRun.stateDetails());
                        }
                        return null;
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "Spark SQL timed out for job " + jrId);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }
}
