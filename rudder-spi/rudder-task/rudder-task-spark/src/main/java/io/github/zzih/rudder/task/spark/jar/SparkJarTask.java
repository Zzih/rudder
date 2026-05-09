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

package io.github.zzih.rudder.task.spark.jar;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.JobTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;
import io.github.zzih.rudder.task.api.task.executor.ShellRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Spark JAR 任务。自包含执行:在 handle() 内拼 spark-submit 命令,通过 {@link ShellRunner} 拉子进程,
 * 从 stdout 抓 YARN appId,然后轮询 YARN 状态直到终止。
 */
@Slf4j
public class SparkJarTask extends AbstractTask implements JobTask {

    /** spark-submit YARN cluster mode 在 stdout 打 "tracking URL: ..."。client / k8s / local 模式可能没有。 */
    private static final Pattern TRACKING_URL_PATTERN = Pattern.compile("tracking URL:\\s*(\\S+)");
    private static final String DEFAULT_WORK_DIR = "/tmp/rudder/jar-tasks";

    protected final SparkJarTaskParams params;
    protected volatile TaskStatus status = TaskStatus.SUBMITTED;
    protected volatile String appId;
    protected volatile String trackingUrl;

    public SparkJarTask(TaskExecutionContext ctx, SparkJarTaskParams params) {
        super(ctx);
        this.params = params;
    }

    @Override
    public void init() {
        status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        String jarPath = resolveJarPath();
        log.info("提交 Spark JAR: mainClass={}, jar={}", params.getMainClass(), jarPath);
        try {
            List<String> cmd = buildCommand(jarPath);
            log.info("spark-submit: {}", String.join(" ", cmd));

            String[] appIdHolder = {null};
            String[] trackingUrlHolder = {null};
            int exitCode = ShellRunner.executeShell(workDir(), ctx.getEnvVars(), cmd, line -> {
                log.info("[spark-submit] {}", line);
                if (appIdHolder[0] == null) {
                    appIdHolder[0] = ShellRunner.extractYarnAppId(line);
                }
                if (trackingUrlHolder[0] == null) {
                    Matcher m = TRACKING_URL_PATTERN.matcher(line);
                    if (m.find()) {
                        trackingUrlHolder[0] = m.group(1);
                    }
                }
            });

            if (exitCode != 0 && appIdHolder[0] == null) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "spark-submit exited with code " + exitCode);
            }

            this.appId =
                    appIdHolder[0] != null ? appIdHolder[0] : "spark-" + UUID.randomUUID().toString().substring(0, 8);
            this.trackingUrl = trackingUrlHolder[0];
            log.info("Spark JAR 已提交, appId={}, trackingUrl={}", appId, trackingUrl);

            ShellRunner.YarnAppState finalState = ShellRunner.pollUntilTerminal(appId, Duration.ofSeconds(5));
            this.status = finalState == ShellRunner.YarnAppState.SUCCEEDED ? TaskStatus.SUCCESS : TaskStatus.FAILED;
            log.info("Spark JAR 执行完成: {}", status);
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        }
    }

    @Override
    public void cancel() {
        status = TaskStatus.CANCELLED;
        ShellRunner.killYarnApplication(appId);
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public String getTrackingUrl() {
        return trackingUrl;
    }

    protected String resolveJarPath() {
        Map<String, String> resolved = ctx.getResolvedFilePaths();
        if (resolved != null && resolved.containsKey("jarPath")) {
            return resolved.get("jarPath");
        }
        return params.getJarPath();
    }

    private String workDir() {
        return ctx.getExecutePath() != null ? ctx.getExecutePath() : DEFAULT_WORK_DIR;
    }

    private List<String> buildCommand(String jarPath) {
        SparkResourceConfig res = params.getResource();
        List<String> cmd = new ArrayList<>();
        cmd.add("spark-submit");
        if (notBlank(params.getAppName())) {
            cmd.add("--name");
            cmd.add(params.getAppName());
        }
        if (notBlank(params.getMaster())) {
            cmd.add("--master");
            cmd.add(params.getMaster());
        }
        if (notBlank(params.getDeployMode())) {
            cmd.add("--deploy-mode");
            cmd.add(params.getDeployMode());
        }
        if (notBlank(params.getQueue())) {
            cmd.add("--queue");
            cmd.add(params.getQueue());
        }
        if (notBlank(params.getMainClass())) {
            cmd.add("--class");
            cmd.add(params.getMainClass());
        }
        if (res != null) {
            if (res.getDriverCores() > 0) {
                cmd.add("--driver-cores");
                cmd.add(String.valueOf(res.getDriverCores()));
            }
            if (notBlank(res.getDriverMemory())) {
                cmd.add("--driver-memory");
                cmd.add(res.getDriverMemory());
            }
            if (res.getExecutorCores() > 0) {
                cmd.add("--executor-cores");
                cmd.add(String.valueOf(res.getExecutorCores()));
            }
            if (notBlank(res.getExecutorMemory())) {
                cmd.add("--executor-memory");
                cmd.add(res.getExecutorMemory());
            }
            if (res.getExecutorInstances() > 0) {
                cmd.add("--num-executors");
                cmd.add(String.valueOf(res.getExecutorInstances()));
            }
        }
        if (params.getEngineParams() != null) {
            for (var entry : params.getEngineParams().entrySet()) {
                cmd.add("--conf");
                cmd.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        cmd.add(jarPath);
        if (notBlank(params.getArgs())) {
            for (String arg : params.getArgs().split("\\s+")) {
                if (!arg.isBlank()) {
                    cmd.add(arg);
                }
            }
        }
        return cmd;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
