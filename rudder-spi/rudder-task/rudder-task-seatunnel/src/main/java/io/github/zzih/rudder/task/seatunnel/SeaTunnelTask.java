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

package io.github.zzih.rudder.task.seatunnel;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.parser.TaskOutputParameterParser;
import io.github.zzih.rudder.task.api.parser.VarPoolFilter;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.LocalTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeaTunnelTask extends AbstractTask implements LocalTask {

    private static final String SEATUNNEL_HOME = System.getProperty(
            "rudder.execution.seatunnel-home",
            System.getenv().getOrDefault("SEATUNNEL_HOME", "/opt/bigdata/seatunnel"));
    private final SeaTunnelTaskParams params;
    private volatile TaskStatus status = TaskStatus.SUBMITTED;
    private volatile Process process;
    private final TaskOutputParameterParser outputParser = new TaskOutputParameterParser();

    public SeaTunnelTask(TaskExecutionContext ctx, SeaTunnelTaskParams params) {
        super(ctx);
        this.params = params;
    }

    @Override
    public void init() throws TaskException {
        if (params.getContent() == null || params.getContent().isBlank()) {
            throw new TaskException(TaskErrorCode.TASK_SEATUNNEL_CONFIG_EMPTY);
        }
        status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        log.info("Status -> {}", TaskStatus.RUNNING);
        log.info("Executing SeaTunnel task (deployMode={})...", params.getDeployMode());

        Path tempConfig = null;
        try {
            // 将配置内容写入临时文件
            tempConfig = Files.createTempFile("seatunnel-", ".conf");
            Files.writeString(tempConfig, params.getContent());
            log.info("Config written to {}", tempConfig);

            // 构建命令行
            List<String> command = buildCommand(tempConfig.toString());
            log.info("Command: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                    outputParser.appendParseLog(line);
                }
            }

            boolean finished;
            if (ctx.getTimeoutSeconds() > 0) {
                finished = process.waitFor(ctx.getTimeoutSeconds(), TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }
            if (!finished) {
                process.destroyForcibly();
                status = TaskStatus.TIMEOUT;
                log.info("Status -> {}", TaskStatus.TIMEOUT);
                throw new TaskException(TaskErrorCode.TASK_SEATUNNEL_TIMEOUT);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                status = TaskStatus.SUCCESS;
                log.info("SeaTunnel task completed successfully (exit code 0)");
                log.info("Status -> {}", TaskStatus.SUCCESS);
            } else {
                status = TaskStatus.FAILED;
                log.error("SeaTunnel task failed with exit code: {}", exitCode);
                log.info("Status -> {}", TaskStatus.FAILED);
                throw new TaskException(TaskErrorCode.TASK_EXIT_CODE, exitCode);
            }
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            status = TaskStatus.FAILED;
            log.error("Error: {}", e.getMessage());
            log.info("Status -> {}", TaskStatus.FAILED);
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, e.getMessage());
        } finally {
            if (tempConfig != null) {
                try {
                    Files.deleteIfExists(tempConfig);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private List<String> buildCommand(String configPath) {
        String deployMode = params.getDeployMode() != null ? params.getDeployMode() : "cluster";

        List<String> cmd = new ArrayList<>();
        cmd.add(SEATUNNEL_HOME + "/bin/seatunnel.sh");
        cmd.add("--config");
        cmd.add(configPath);
        // SeaTunnel 2.3.13: deploy mode 用 -m 参数，默认就是 cluster
        cmd.add("-m");
        cmd.add(deployMode);
        return cmd;
    }

    @Override
    public void cancel() throws TaskException {
        status = TaskStatus.CANCELLED;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    @Override
    public List<Property> getVarPool() {
        return VarPoolFilter.filterByMap(outputParser.getOutputParams(), ctx.getOutputParamsSpec());
    }
}
