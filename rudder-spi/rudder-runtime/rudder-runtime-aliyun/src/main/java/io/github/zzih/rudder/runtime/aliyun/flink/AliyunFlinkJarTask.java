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
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTask;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTaskParams;
import io.github.zzih.rudder.task.flink.jar.FlinkResourceConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.aliyun.ververica20220718.Client;
import com.aliyun.ververica20220718.models.Artifact;
import com.aliyun.ververica20220718.models.BasicResourceSetting;
import com.aliyun.ververica20220718.models.BasicResourceSettingSpec;
import com.aliyun.ververica20220718.models.BriefDeploymentTarget;
import com.aliyun.ververica20220718.models.CreateDeploymentHeaders;
import com.aliyun.ververica20220718.models.CreateDeploymentRequest;
import com.aliyun.ververica20220718.models.CreateDeploymentResponse;
import com.aliyun.ververica20220718.models.Deployment;
import com.aliyun.ververica20220718.models.JarArtifact;
import com.aliyun.ververica20220718.models.StreamingResourceSetting;

import lombok.extern.slf4j.Slf4j;

/** 阿里云 VVP Flink JAR 任务,通过 createDeployment 提交;批任务轮询到 FINISHED,流任务提交后即返回。 */
@Slf4j
public class AliyunFlinkJarTask extends FlinkJarTask {

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int POLL_MAX_ATTEMPTS = 720;

    private final AliyunRuntimeProperties props;
    private final Client vvpClient;

    public AliyunFlinkJarTask(TaskExecutionContext ctx, FlinkJarTaskParams params,
                              AliyunRuntimeProperties props, Client vvpClient) {
        super(ctx, params);
        this.props = props;
        this.vvpClient = vvpClient;
    }

    @Override
    public void handle() throws TaskException {
        boolean streaming = isStreaming();
        String jarPath = resolveJarPath();
        String appName = notBlank(params.getAppName()) ? params.getAppName() : "rudder-flink-jar";
        String deploymentId = createDeployment(jarPath, appName, streaming);
        this.appId = deploymentId;
        log.info("VVP Flink {} JAR deployment created, id={}", streaming ? "streaming" : "batch", deploymentId);

        try {
            if (!streaming) {
                pollUntilTerminal(deploymentId);
            }
            this.status = TaskStatus.SUCCESS;
        } catch (TaskException e) {
            this.status = TaskStatus.FAILED;
            throw e;
        }
    }

    @Override
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        if (appId == null) {
            return;
        }
        log.info("Cancelling Aliyun VVP JAR deployment: {}", appId);
        VvpSavepointUtils.cancelDeployment(props, vvpClient, appId);
    }

    @Override
    public boolean isStreaming() {
        return ExecutionMode.STREAMING.name().equalsIgnoreCase(params.getExecutionMode());
    }

    private String createDeployment(String jarPath, String appName, boolean streaming) throws TaskException {
        String ns = props.getFlink().getNamespace();

        JarArtifact jarArtifact = new JarArtifact()
                .setJarUri(jarPath)
                .setEntryClass(notBlank(params.getMainClass()) ? params.getMainClass() : "")
                .setMainArgs(params.getArgs() != null ? params.getArgs() : "");

        Artifact artifact = new Artifact().setKind("JAR").setJarArtifact(jarArtifact);

        Map<String, Object> flinkConf = new HashMap<>();
        if (params.getEngineParams() != null) {
            flinkConf.putAll(params.getEngineParams());
        }
        FlinkResourceConfig res = params.getResource();
        if (res != null && res.getParallelism() > 0) {
            flinkConf.put("parallelism.default", String.valueOf(res.getParallelism()));
        }

        BriefDeploymentTarget target = new BriefDeploymentTarget()
                .setMode("PER_JOB")
                .setName("default-queue");

        BasicResourceSetting basicResource = new BasicResourceSetting()
                .setJobmanagerResourceSettingSpec(new BasicResourceSettingSpec()
                        .setCpu(1.0)
                        .setMemory(res != null && notBlank(res.getJobManagerMemory()) ? res.getJobManagerMemory()
                                : "2Gi"))
                .setTaskmanagerResourceSettingSpec(new BasicResourceSettingSpec()
                        .setCpu(1.0)
                        .setMemory(res != null && notBlank(res.getTaskManagerMemory()) ? res.getTaskManagerMemory()
                                : "4Gi"));

        StreamingResourceSetting resourceSetting = new StreamingResourceSetting()
                .setResourceSettingMode("BASIC")
                .setBasicResourceSetting(basicResource);

        Deployment deployment = new Deployment()
                .setName(appName)
                .setArtifact(artifact)
                .setFlinkConf(flinkConf)
                .setDeploymentTarget(target)
                .setExecutionMode(streaming ? ExecutionMode.STREAMING.name() : ExecutionMode.BATCH.name())
                .setStreamingResourceSetting(resourceSetting);

        CreateDeploymentRequest createReq = new CreateDeploymentRequest().setBody(deployment);
        CreateDeploymentHeaders headers = new CreateDeploymentHeaders()
                .setWorkspace(props.getFlink().getWorkspaceId());

        try {
            log.info("Creating VVP Flink JAR deployment: {}", appName);
            CreateDeploymentResponse resp = vvpClient.createDeploymentWithOptions(
                    ns, createReq, headers, new com.aliyun.teautil.models.RuntimeOptions());
            var body = resp.getBody();
            if (body == null || body.getData() == null || body.getData().getDeploymentId() == null) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "VVP Flink JAR: invalid response, no deploymentId returned");
            }
            return body.getData().getDeploymentId();
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Failed to create Flink JAR deployment: " + e.getMessage());
        }
    }

    private void pollUntilTerminal(String deploymentId) throws TaskException {
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
                                throw new RuntimeException("VVP Flink JAR deployment failed: " + deploymentId);
                            case "CANCELLED" ->
                                throw new RuntimeException("VVP Flink JAR deployment cancelled: " + deploymentId);
                            default -> null;
                        };
                    },
                    Duration.ofSeconds(POLL_INTERVAL_SECONDS), POLL_MAX_ATTEMPTS,
                    "VVP Flink JAR deployment timed out: " + deploymentId);
        } catch (RuntimeException e) {
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
