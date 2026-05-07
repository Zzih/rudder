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
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTask;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTaskParams;

import java.time.Duration;
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
import software.amazon.awssdk.services.kinesisanalyticsv2.model.S3ContentLocation;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.StartApplicationRequest;
import software.amazon.awssdk.services.kinesisanalyticsv2.model.StopApplicationRequest;

/**
 * AWS Managed Flink JAR 任务,通过 createApplication + startApplication 提交。
 * 批任务轮询到 READY,流任务提交后即返回。
 */
@Slf4j
public class AwsFlinkJarTask extends FlinkJarTask {

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int POLL_MAX_ATTEMPTS = 720;

    private final AwsRuntimeProperties props;
    private final KinesisAnalyticsV2Client flinkClient;

    public AwsFlinkJarTask(TaskExecutionContext ctx, FlinkJarTaskParams params,
                           AwsRuntimeProperties props, KinesisAnalyticsV2Client flinkClient) {
        super(ctx, params);
        this.props = props;
        this.flinkClient = flinkClient;
    }

    @Override
    public boolean isStreaming() {
        return ExecutionMode.STREAMING.name().equalsIgnoreCase(params.getExecutionMode());
    }

    @Override
    public void handle() throws TaskException {
        boolean streaming = isStreaming();
        String jarPath = resolveJarPath();
        String appName = notBlank(params.getAppName())
                ? params.getAppName()
                : "rudder-flink-jar-" + System.currentTimeMillis();

        CodeContent codeContent = CodeContent.builder()
                .s3ContentLocation(S3ContentLocation.builder()
                        .bucketARN(extractBucketArn(jarPath))
                        .fileKey(extractFileKey(jarPath))
                        .build())
                .build();

        ApplicationConfiguration appConfig = ApplicationConfiguration.builder()
                .applicationCodeConfiguration(ApplicationCodeConfiguration.builder()
                        .codeContent(codeContent)
                        .codeContentType(CodeContentType.ZIPFILE)
                        .build())
                .environmentProperties(EnvironmentProperties.builder()
                        .propertyGroups(PropertyGroup.builder()
                                .propertyGroupId("FlinkApplicationProperties")
                                .propertyMap(params.getEngineParams() != null ? params.getEngineParams() : Map.of())
                                .build())
                        .build())
                .build();

        try {
            log.info("Creating Managed Flink JAR application: {}", appName);
            flinkClient.createApplication(CreateApplicationRequest.builder()
                    .applicationName(appName)
                    .runtimeEnvironment(RuntimeEnvironment.fromValue(props.getFlink().getRuntimeEnvironment()))
                    .serviceExecutionRole(props.getFlink().getServiceExecutionRole())
                    .applicationConfiguration(appConfig)
                    .build());
            flinkClient.startApplication(StartApplicationRequest.builder()
                    .applicationName(appName)
                    .build());

            this.appId = appName;
            log.info("Managed Flink JAR application started: {}", appName);

            if (!streaming) {
                pollUntilTerminal(appName);
            }
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        } catch (Exception e) {
            this.status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to create Managed Flink JAR application: " + e.getMessage());
        }
    }

    @Override
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        if (appId == null) {
            return;
        }
        try {
            log.info("Stopping AWS Managed Flink application: {}", appId);
            flinkClient.stopApplication(StopApplicationRequest.builder()
                    .applicationName(appId)
                    .force(false)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to stop AWS Managed Flink application {}: {}", appId, e.getMessage());
        }
    }

    private void pollUntilTerminal(String appName) throws TaskException {
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
                            log.warn("Error polling Managed Flink JAR status: {}", e.getMessage());
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
                            throw new RuntimeException("Flink JAR application deleted: " + appName);
                        }
                        return null;
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "Managed Flink JAR application timed out: " + appName);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    /** s3://my-bucket/path/to/jar.jar → arn:aws:s3:::my-bucket */
    private static String extractBucketArn(String s3Path) {
        String bucket = s3Path.replaceFirst("s3://", "").split("/")[0];
        return "arn:aws:s3:::" + bucket;
    }

    /** s3://my-bucket/path/to/jar.jar → path/to/jar.jar */
    private static String extractFileKey(String s3Path) {
        String withoutScheme = s3Path.replaceFirst("s3://", "");
        int slashIdx = withoutScheme.indexOf('/');
        return slashIdx >= 0 ? withoutScheme.substring(slashIdx + 1) : "";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
