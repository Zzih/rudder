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
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.spark.jar.SparkJarTask;
import io.github.zzih.rudder.task.spark.jar.SparkJarTaskParams;
import io.github.zzih.rudder.task.spark.jar.SparkResourceConfig;

import java.time.Duration;
import java.util.Arrays;
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

/** AWS EMR Serverless Spark JAR 任务,通过 startJobRun 提交,轮询直到 jobRun 终态。 */
@Slf4j
public class AwsSparkJarTask extends SparkJarTask {

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int POLL_MAX_ATTEMPTS = 720;

    private final AwsRuntimeProperties props;
    private final EmrServerlessClient emrClient;

    public AwsSparkJarTask(TaskExecutionContext ctx, SparkJarTaskParams params,
                           AwsRuntimeProperties props, EmrServerlessClient emrClient) {
        super(ctx, params);
        this.props = props;
        this.emrClient = emrClient;
    }

    @Override
    public void handle() throws TaskException {
        String emrAppId = props.getSpark().getApplicationId();
        String jarPath = resolveJarPath();
        log.info("Submitting Spark JAR to EMR Serverless: {}", jarPath);

        StartJobRunRequest startReq = buildStartRequest(jarPath, emrAppId);

        try {
            StartJobRunResponse resp = emrClient.startJobRun(startReq);
            String jobRunId = resp.jobRunId();
            this.appId = jobRunId;
            log.info("Spark JAR submitted, jobRunId={}", jobRunId);
            pollUntilTerminal(emrAppId, jobRunId);
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        } catch (Exception e) {
            this.status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to submit Spark JAR to EMR Serverless: " + e.getMessage());
        }
    }

    @Override
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        if (appId == null) {
            return;
        }
        try {
            log.info("Cancelling AWS Spark JAR jobRun: {}", appId);
            emrClient.cancelJobRun(CancelJobRunRequest.builder()
                    .applicationId(props.getSpark().getApplicationId())
                    .jobRunId(appId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to cancel AWS Spark JAR jobRun {}: {}", appId, e.getMessage());
        }
    }

    private StartJobRunRequest buildStartRequest(String jarPath, String emrAppId) {
        StringBuilder sparkParams = new StringBuilder();
        if (params.getMainClass() != null) {
            sparkParams.append("--class ").append(params.getMainClass());
        }
        SparkResourceConfig res = params.getResource();
        if (res != null) {
            if (res.getDriverCores() > 0) {
                sparkParams.append(" --conf spark.driver.cores=").append(res.getDriverCores());
            }
            if (notBlank(res.getDriverMemory())) {
                sparkParams.append(" --conf spark.driver.memory=").append(res.getDriverMemory());
            }
            if (res.getExecutorCores() > 0) {
                sparkParams.append(" --conf spark.executor.cores=").append(res.getExecutorCores());
            }
            if (notBlank(res.getExecutorMemory())) {
                sparkParams.append(" --conf spark.executor.memory=").append(res.getExecutorMemory());
            }
            if (res.getExecutorInstances() > 0) {
                sparkParams.append(" --conf spark.executor.instances=").append(res.getExecutorInstances());
            }
        }
        if (params.getEngineParams() != null) {
            params.getEngineParams().forEach((k, v) -> sparkParams.append(" --conf ").append(k).append('=').append(v));
        }

        List<String> argsList = List.of();
        if (notBlank(params.getArgs())) {
            argsList = Arrays.asList(params.getArgs().split("\\s+"));
        }

        SparkSubmit sparkSubmit = SparkSubmit.builder()
                .entryPoint(jarPath)
                .entryPointArguments(argsList)
                .sparkSubmitParameters(sparkParams.toString().trim())
                .build();

        return StartJobRunRequest.builder()
                .applicationId(emrAppId)
                .executionRoleArn(props.getSpark().getExecutionRoleArn())
                .jobDriver(JobDriver.builder().sparkSubmit(sparkSubmit).build())
                .name(notBlank(params.getAppName()) ? params.getAppName() : "rudder-spark-jar")
                .build();
    }

    private void pollUntilTerminal(String emrAppId, String jobRunId) throws TaskException {
        try {
            PollingUtils.poll(
                    () -> {
                        try {
                            GetJobRunResponse resp = emrClient.getJobRun(GetJobRunRequest.builder()
                                    .applicationId(emrAppId)
                                    .jobRunId(jobRunId)
                                    .build());
                            return resp.jobRun();
                        } catch (Exception e) {
                            log.warn("Error polling EMR Serverless JAR job: {}", e.getMessage());
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
                            throw new RuntimeException("Spark JAR job " + state + ": " + jobRun.stateDetails());
                        }
                        return null;
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "Spark JAR timed out for job " + jobRunId);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
