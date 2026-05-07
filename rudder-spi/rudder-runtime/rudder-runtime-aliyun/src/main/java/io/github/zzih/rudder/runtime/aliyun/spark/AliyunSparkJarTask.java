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
import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aliyun.AliyunRuntimeProperties;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.spark.jar.SparkJarTask;
import io.github.zzih.rudder.task.spark.jar.SparkJarTaskParams;
import io.github.zzih.rudder.task.spark.jar.SparkResourceConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.aliyun.emr_serverless_spark20230808.Client;
import com.aliyun.emr_serverless_spark20230808.models.CancelJobRunRequest;
import com.aliyun.emr_serverless_spark20230808.models.GetJobRunRequest;
import com.aliyun.emr_serverless_spark20230808.models.GetJobRunResponse;
import com.aliyun.emr_serverless_spark20230808.models.JobDriver;
import com.aliyun.emr_serverless_spark20230808.models.StartJobRunRequest;
import com.aliyun.emr_serverless_spark20230808.models.StartJobRunResponse;

import lombok.extern.slf4j.Slf4j;

/** 阿里云 Serverless Spark JAR 任务,通过 startJobRun 提交,轮询直到 jobRun 终态。 */
@Slf4j
public class AliyunSparkJarTask extends SparkJarTask {

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int POLL_MAX_ATTEMPTS = 720;

    private final AliyunRuntimeProperties props;
    private final Client sparkClient;

    public AliyunSparkJarTask(TaskExecutionContext ctx, SparkJarTaskParams params,
                              AliyunRuntimeProperties props, Client sparkClient) {
        super(ctx, params);
        this.props = props;
        this.sparkClient = sparkClient;
    }

    @Override
    public void handle() throws TaskException {
        String wsId = props.getSpark().getWorkspaceId();
        String jarPath = resolveJarPath();
        log.info("Submitting Spark JAR to Aliyun Serverless Spark: {}", jarPath);

        StartJobRunRequest startReq = buildStartRequest(jarPath);

        try {
            StartJobRunResponse resp = sparkClient.startJobRun(wsId, startReq);
            String jobRunId = resp.getBody().getJobRunId();
            if (jobRunId == null) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "Aliyun Spark JAR: invalid response, no jobRunId returned");
            }
            this.appId = jobRunId;
            log.info("Spark JAR submitted, jobRunId={}", jobRunId);

            pollUntilTerminal(wsId, jobRunId);
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        } catch (Exception e) {
            this.status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to submit Spark JAR: " + e.getMessage());
        }
    }

    @Override
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        if (appId == null) {
            return;
        }
        try {
            String wsId = props.getSpark().getWorkspaceId();
            log.info("Cancelling Aliyun Spark JAR jobRun: {}", appId);
            sparkClient.cancelJobRun(wsId, appId, new CancelJobRunRequest());
        } catch (Exception e) {
            log.warn("Failed to cancel Aliyun Spark JAR jobRun {}: {}", appId, e.getMessage());
        }
    }

    private StartJobRunRequest buildStartRequest(String jarPath) {
        StringBuilder paramStr = new StringBuilder();
        if (params.getMainClass() != null) {
            paramStr.append("--class ").append(params.getMainClass());
        }
        SparkResourceConfig res = params.getResource();
        if (res != null) {
            if (res.getDriverCores() > 0) {
                paramStr.append(" --conf spark.driver.cores=").append(res.getDriverCores());
            }
            if (notBlank(res.getDriverMemory())) {
                paramStr.append(" --conf spark.driver.memory=").append(res.getDriverMemory());
            }
            if (res.getExecutorCores() > 0) {
                paramStr.append(" --conf spark.executor.cores=").append(res.getExecutorCores());
            }
            if (notBlank(res.getExecutorMemory())) {
                paramStr.append(" --conf spark.executor.memory=").append(res.getExecutorMemory());
            }
            if (res.getExecutorInstances() > 0) {
                paramStr.append(" --conf spark.executor.instances=").append(res.getExecutorInstances());
            }
        }
        if (params.getEngineParams() != null) {
            params.getEngineParams().forEach((k, v) -> paramStr.append(" --conf ").append(k).append('=').append(v));
        }

        List<String> argsList = List.of();
        if (notBlank(params.getArgs())) {
            argsList = Arrays.asList(params.getArgs().split("\\s+"));
        }

        JobDriver.JobDriverSparkSubmit sparkSubmit = new JobDriver.JobDriverSparkSubmit()
                .setEntryPoint(jarPath)
                .setEntryPointArguments(argsList)
                .setSparkSubmitParameters(paramStr.toString().trim());

        return new StartJobRunRequest()
                .setName(notBlank(params.getAppName()) ? params.getAppName() : "rudder-spark-jar")
                .setResourceQueueId(props.getSpark().getResourceQueueId())
                .setCodeType("JAR")
                .setJobDriver(new JobDriver().setSparkSubmit(sparkSubmit));
    }

    private void pollUntilTerminal(String wsId, String jobRunId) throws TaskException {
        try {
            PollingUtils.poll(
                    () -> {
                        try {
                            GetJobRunResponse resp = sparkClient.getJobRun(
                                    wsId, jobRunId, new GetJobRunRequest());
                            var jobRun = resp.getBody().getJobRun();
                            if (trackingUrl == null && jobRun.getWebUI() != null) {
                                trackingUrl = jobRun.getWebUI();
                            }
                            return jobRun.getState();
                        } catch (Exception e) {
                            log.warn("Error polling Spark JAR state: {}", e.getMessage());
                            return null;
                        }
                    },
                    state -> {
                        if (state == null) {
                            return null;
                        }
                        return switch (state) {
                            case "Success" -> Boolean.TRUE;
                            case "Failed" ->
                                throw new RuntimeException("Aliyun Spark JAR failed: jobRunId=" + jobRunId);
                            case "Cancelled", "Cancelling" ->
                                throw new RuntimeException("Aliyun Spark JAR cancelled: jobRunId=" + jobRunId);
                            default -> null;
                        };
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "Aliyun Spark JAR timed out for jobRunId=" + jobRunId);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
