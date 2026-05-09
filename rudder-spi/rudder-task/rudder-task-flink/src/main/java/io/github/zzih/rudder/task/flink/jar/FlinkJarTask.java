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

package io.github.zzih.rudder.task.flink.jar;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.JobTask;
import io.github.zzih.rudder.task.api.task.enums.ExecutionMode;
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
 * Flink JAR 任务。自包含执行:在 handle() 内拼 flink run / run-application 命令,通过
 * {@link ShellRunner} 拉子进程,从 stdout 抓 Flink jobId 或 YARN appId,然后(application 模式)
 * 轮询 YARN 状态直到终止。
 */
@Slf4j
public class FlinkJarTask extends AbstractTask implements JobTask {

    private static final Pattern JOB_ID_PATTERN = Pattern.compile("Job has been submitted with JobID ([a-f0-9]+)");
    /** Flink CLI 在 stdout 打 "JobManager Web Interface: ..." / "Found Web Interface host:port"。 */
    private static final Pattern WEB_INTERFACE_PATTERN = Pattern.compile("Web Interface[\\s:]+(\\S+)");
    private static final String DEFAULT_WORK_DIR = "/tmp/rudder/jar-tasks";

    protected final FlinkJarTaskParams params;
    protected volatile TaskStatus status = TaskStatus.SUBMITTED;
    protected volatile String appId;
    protected volatile String trackingUrl;

    public FlinkJarTask(TaskExecutionContext ctx, FlinkJarTaskParams params) {
        super(ctx);
        this.params = params;
    }

    @Override
    public void init() {
        status = TaskStatus.RUNNING;
    }

    @Override
    public boolean isStreaming() {
        return ExecutionMode.STREAMING.name().equalsIgnoreCase(ctx.getExecutionMode());
    }

    @Override
    public void handle() throws TaskException {
        String jarPath = resolveJarPath();
        log.info("提交 Flink JAR: mainClass={}, jar={}", params.getMainClass(), jarPath);
        boolean appMode = isApplicationMode(params.getDeployMode());

        try {
            List<String> cmd = buildCommand(jarPath, appMode);
            log.info("flink: {}", String.join(" ", cmd));

            String[] jobIdHolder = {null};
            String[] yarnAppIdHolder = {null};
            String[] trackingUrlHolder = {null};
            int exitCode = ShellRunner.executeShell(workDir(), ctx.getEnvVars(), cmd, line -> {
                log.info("[flink] {}", line);
                if (jobIdHolder[0] == null) {
                    Matcher m = JOB_ID_PATTERN.matcher(line);
                    if (m.find()) {
                        jobIdHolder[0] = m.group(1);
                    }
                }
                if (appMode && yarnAppIdHolder[0] == null) {
                    yarnAppIdHolder[0] = ShellRunner.extractYarnAppId(line);
                }
                if (trackingUrlHolder[0] == null) {
                    Matcher m = WEB_INTERFACE_PATTERN.matcher(line);
                    if (m.find()) {
                        String raw = m.group(1).replaceAll("[',]+$", "");
                        trackingUrlHolder[0] = raw.startsWith("http") ? raw : "http://" + raw;
                    }
                }
            });

            if (exitCode != 0) {
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "flink run exited with code " + exitCode);
            }

            this.appId = appMode && yarnAppIdHolder[0] != null
                    ? yarnAppIdHolder[0]
                    : (jobIdHolder[0] != null ? jobIdHolder[0]
                            : "flink-" + UUID.randomUUID().toString().substring(0, 8));
            this.trackingUrl = trackingUrlHolder[0];
            log.info("Flink JAR 已提交, appId={}, trackingUrl={}", appId, trackingUrl);

            // application 模式 spark-submit 风格阻塞等 YARN 终态;run 模式 flink CLI 已经同步执行完
            if (appMode && yarnAppIdHolder[0] != null) {
                ShellRunner.YarnAppState finalState =
                        ShellRunner.pollUntilTerminal(appId, Duration.ofSeconds(5));
                this.status = finalState == ShellRunner.YarnAppState.SUCCEEDED
                        ? TaskStatus.SUCCESS
                        : TaskStatus.FAILED;
            } else {
                this.status = TaskStatus.SUCCESS;
            }
            log.info("Flink JAR 执行完成: {}", status);
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
        // killYarnApplication 内部对非 YARN 格式 ID 自动 no-op,这里不再前置判断
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

    private static boolean isApplicationMode(String target) {
        return target != null && target.endsWith("-application");
    }

    private List<String> buildCommand(String jarPath, boolean appMode) {
        FlinkResourceConfig res = params.getResource();
        List<String> cmd = new ArrayList<>();
        cmd.add("flink");
        cmd.add(appMode ? "run-application" : "run");

        String target = params.getDeployMode();
        if (notBlank(target) && !"local".equalsIgnoreCase(target)) {
            cmd.add("-t");
            cmd.add(target);
        }
        if (notBlank(params.getAppName())) {
            cmd.add("-Dpipeline.name=" + params.getAppName());
        }
        if (notBlank(params.getSavepointPath())) {
            cmd.add("-s");
            cmd.add(params.getSavepointPath());
            if (params.isAllowNonRestoredState()) {
                cmd.add("--allowNonRestoredState");
            }
        }
        if (notBlank(params.getMainClass())) {
            cmd.add("-c");
            cmd.add(params.getMainClass());
        }
        if (res != null) {
            if (res.getParallelism() > 0) {
                cmd.add("-p");
                cmd.add(String.valueOf(res.getParallelism()));
            }
            if (notBlank(res.getJobManagerMemory())) {
                cmd.add("-Djobmanager.memory.process.size=" + res.getJobManagerMemory());
            }
            if (notBlank(res.getTaskManagerMemory())) {
                cmd.add("-Dtaskmanager.memory.process.size=" + res.getTaskManagerMemory());
            }
        }
        // executionMode 翻译成 flink conf
        if (notBlank(params.getExecutionMode())) {
            ExecutionMode mode = ExecutionMode.valueOf(params.getExecutionMode().toUpperCase());
            cmd.add("-Dexecution.runtime-mode=" + mode.name().toLowerCase());
        }
        if (params.getEngineParams() != null) {
            for (var entry : params.getEngineParams().entrySet()) {
                cmd.add("-D" + entry.getKey() + "=" + entry.getValue());
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
