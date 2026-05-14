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
import io.github.zzih.rudder.common.utils.process.ProcessUtils;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.parser.TaskOutputParameterParser;
import io.github.zzih.rudder.task.api.parser.VarPoolFilter;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.LocalTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeaTunnelTask extends AbstractTask implements LocalTask {

    private final SeaTunnelTaskParams params;
    private volatile TaskStatus status = TaskStatus.SUBMITTED;
    private volatile ProcessUtils.RunningProcess runningProcess;
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
        log.info("Final seatunnel config (deployMode={}):\n{}", params.getDeployMode(), params.getContent());

        Path tempConfig = null;
        try {
            tempConfig = Files.createTempFile("seatunnel-", ".conf");
            Files.writeString(tempConfig, params.getContent());
            log.info("Config written to {}", tempConfig);

            List<String> command = buildCommand(tempConfig.toString());
            log.info("Command: {}", String.join(" ", command));

            runningProcess = ProcessUtils.start(
                    command.toArray(new String[0]),
                    ctx.getEnvVars(),
                    line -> {
                        log.info(line);
                        outputParser.appendParseLog(line);
                    });
            ProcessUtils.Result result = runningProcess.waitFor(ctx.getTimeoutSeconds());

            if (result.timedOut()) {
                status = TaskStatus.TIMEOUT;
                log.info("Status -> {}", TaskStatus.TIMEOUT);
                throw new TaskException(TaskErrorCode.TASK_SEATUNNEL_TIMEOUT);
            }
            if (result.isSuccess()) {
                status = TaskStatus.SUCCESS;
                log.info("SeaTunnel task completed successfully (exit code 0)");
                log.info("Status -> {}", TaskStatus.SUCCESS);
            } else {
                status = TaskStatus.FAILED;
                log.error("SeaTunnel task failed with exit code: {}", result.exitCode());
                log.info("Status -> {}", TaskStatus.FAILED);
                throw new TaskException(TaskErrorCode.TASK_EXIT_CODE, result.exitCode());
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
        cmd.add("seatunnel.sh");
        cmd.add("--config");
        cmd.add(configPath);
        cmd.add("-m");
        cmd.add(deployMode);
        return cmd;
    }

    @Override
    public void cancel() throws TaskException {
        status = TaskStatus.CANCELLED;
        if (runningProcess != null) {
            runningProcess.destroy();
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
