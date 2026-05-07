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

package io.github.zzih.rudder.runtime.aws.flink;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aws.AwsRuntimeProperties;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.flink.sql.FlinkSqlTask;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2Client;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.ApplicationCodeConfiguration;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.ApplicationConfiguration;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.ApplicationStatus;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.CodeContent;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.CodeContentType;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.CreateApplicationRequest;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.DescribeApplicationRequest;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.DescribeApplicationResponse;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.EnvironmentProperties;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.PropertyGroup;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.RuntimeEnvironment;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.StartApplicationRequest;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.StopApplicationRequest;

/**
 * AWS Managed Flink SQL 任务,通过 createApplication + startApplication 提交。
 * Managed Flink 不直接返回结果集,sink 按 SQL 任务契约写空数组占位。
 */
@Slf4j
public class AwsFlinkSqlTask extends FlinkSqlTask {

    private static final int POLL_INTERVAL_SECONDS = 2;
    private static final int POLL_MAX_ATTEMPTS = 600;

    private final AwsRuntimeProperties props;
    private final KinesisAnalyticsV2Client flinkClient;

    public AwsFlinkSqlTask(TaskExecutionContext ctx, SqlTaskParams params,
                           AwsRuntimeProperties props, KinesisAnalyticsV2Client flinkClient) {
        super(ctx, params);
        this.props = props;
        this.flinkClient = flinkClient;
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

        boolean streaming = isStreaming();
        String appName = "rudder-flink-sql-" + System.currentTimeMillis();

        ApplicationConfiguration appConfig = ApplicationConfiguration.builder()
                .applicationCodeConfiguration(ApplicationCodeConfiguration.builder()
                        .codeContent(CodeContent.builder().textContent(params.getSql()).build())
                        .codeContentType(CodeContentType.PLAINTEXT)
                        .build())
                .environmentProperties(EnvironmentProperties.builder()
                        .propertyGroups(PropertyGroup.builder()
                                .propertyGroupId("FlinkApplicationProperties")
                                .propertyMap(params.getEngineParams() != null ? params.getEngineParams() : Map.of())
                                .build())
                        .build())
                .build();

        try {
            log.info("Creating Managed Flink SQL application (mode={})...", streaming ? "streaming" : "batch");
            flinkClient.createApplication(CreateApplicationRequest.builder()
                    .applicationName(appName)
                    .runtimeEnvironment(RuntimeEnvironment.fromValue(props.getFlink().getRuntimeEnvironment()))
                    .serviceExecutionRole(props.getFlink().getServiceExecutionRole())
                    .applicationConfiguration(appConfig)
                    .build());
            flinkClient.startApplication(StartApplicationRequest.builder()
                    .applicationName(appName)
                    .build());

            this.flinkJobId = appName;
            log.info("Managed Flink SQL application started: {}", appName);

            if (!streaming) {
                pollBatchUntilTerminal(appName);
            }
            // sink 关闭(空数组占位,符合 SQL Task 契约)
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
                    "Failed to create Managed Flink SQL application: " + e.getMessage());
        }
    }

    @Override
    public void cancel() throws TaskException {
        this.status = TaskStatus.CANCELLED;
        try {
            if (flinkJobId == null) {
                return;
            }
            try {
                log.info("Stopping AWS Managed Flink SQL application: {}", flinkJobId);
                flinkClient.stopApplication(StopApplicationRequest.builder()
                        .applicationName(flinkJobId)
                        .force(false)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to stop AWS Managed Flink SQL application {}: {}", flinkJobId, e.getMessage());
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

    private void pollBatchUntilTerminal(String appName) throws TaskException {
        try {
            PollingUtils.poll(
                    () -> {
                        try {
                            DescribeApplicationResponse resp = flinkClient.describeApplication(
                                    DescribeApplicationRequest.builder()
                                            .applicationName(appName)
                                            .build());
                            return resp.applicationDetail().applicationStatus();
                        } catch (Exception e) {
                            log.warn("Error polling Managed Flink SQL status: {}", e.getMessage());
                            return null;
                        }
                    },
                    s -> {
                        if (s == null) {
                            return null;
                        }
                        if (s == ApplicationStatus.READY) {
                            return Boolean.TRUE;
                        }
                        if (s == ApplicationStatus.DELETING) {
                            throw new RuntimeException("Flink SQL application deleted: " + appName);
                        }
                        return null;
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "Managed Flink SQL batch application timed out: " + appName);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }
}
