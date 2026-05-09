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

package io.github.zzih.rudder.task.api.task.executor;

import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.common.utils.process.ProcessUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Jar 类 Task 子进程执行 + YARN 状态轮询工具。脚本以 bash -l 启动以 source 用户 profile。 */
public final class ShellRunner {

    private static final Logger log = LoggerFactory.getLogger(ShellRunner.class);
    private static final Pattern YARN_APP_PATTERN = Pattern.compile("application_\\d+_\\d+");

    private ShellRunner() {
    }

    /** 从一行 stdout/stderr 中抽 YARN appId (application_<ts>_<seq>),没匹配到返回 null。 */
    public static String extractYarnAppId(String line) {
        if (line == null) {
            return null;
        }
        var m = YARN_APP_PATTERN.matcher(line);
        return m.find() ? m.group() : null;
    }

    /** YARN 应用状态。Task 拿到后映射到自己的 TaskStatus。 */
    public enum YarnAppState {

        RUNNING, SUCCEEDED, FAILED, KILLED;

        public boolean isTerminal() {
            return this != RUNNING;
        }
    }

    /** 把 cmd 包装成临时 shell 脚本通过 bash -l 执行,envVars 注入子进程 environment。 */
    public static int executeShell(String workDir, Map<String, String> envVars, List<String> cmd,
                                   Consumer<String> lineConsumer) throws IOException, InterruptedException {
        Path scriptFile = createScript(workDir, cmd);
        try {
            ProcessUtils.Result result = ProcessUtils.execute(
                    new String[]{"bash", "-l", scriptFile.toString()},
                    envVars,
                    lineConsumer,
                    0);
            return result.exitCode();
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    /** 通过 yarn application -kill 杀掉 YARN 应用。非 YARN 格式的 ID 直接忽略。 */
    public static void killYarnApplication(String applicationId) {
        if (applicationId == null || !YARN_APP_PATTERN.matcher(applicationId).matches()) {
            return;
        }
        try {
            log.info("Killing YARN application: {}", applicationId);
            ProcessUtils.Result result = ProcessUtils.execute(
                    new String[]{"yarn", "application", "-kill", applicationId},
                    line -> log.info("[yarn-kill] {}", line),
                    30);
            if (result.timedOut()) {
                log.warn("yarn application -kill timed out for {}", applicationId);
            } else if (!result.isSuccess()) {
                log.warn("yarn application -kill exited with code {} for {}", result.exitCode(), applicationId);
            }
        } catch (Exception e) {
            log.warn("Failed to kill YARN application {}: {}", applicationId, e.getMessage());
        }
    }

    /**
     * 通过 yarn application -status 查询应用状态。非 YARN 格式的 ID 直接返回 SUCCEEDED
     * (本地模式 / Spark Thrift / 其他不挂 YARN 的场景视为已完成)。
     */
    public static YarnAppState queryYarnAppState(String applicationId) {
        if (applicationId == null || !YARN_APP_PATTERN.matcher(applicationId).matches()) {
            return YarnAppState.SUCCEEDED;
        }
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("yarn", "application", "-status", applicationId);
            pb.redirectErrorStream(true);
            p = pb.start();
            String output;
            try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = reader.lines()
                        .filter(line -> line.contains("State") || line.contains("Final-State"))
                        .collect(Collectors.joining("\n"));
            }
            p.waitFor(30, TimeUnit.SECONDS);

            if (output.contains("Final-State")) {
                if (output.contains("SUCCEEDED")) {
                    return YarnAppState.SUCCEEDED;
                }
                if (output.contains("FAILED")) {
                    return YarnAppState.FAILED;
                }
                if (output.contains("KILLED")) {
                    return YarnAppState.KILLED;
                }
            }
            if (output.contains("FINISHED")) {
                return YarnAppState.SUCCEEDED;
            }
            return YarnAppState.RUNNING;
        } catch (Exception e) {
            log.warn("Failed to query YARN status for {}: {}", applicationId, e.getMessage());
            return YarnAppState.RUNNING;
        } finally {
            // reader 抛异常 / waitFor timeout 都得保证子进程不残留
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        }
    }

    /** 阻塞轮询 YARN 应用直到终止,返回最终状态。 */
    public static YarnAppState pollUntilTerminal(String applicationId, Duration interval) {
        YarnAppState[] last = {YarnAppState.RUNNING};
        PollingUtils.pollForever(() -> {
            YarnAppState s = queryYarnAppState(applicationId);
            if (s.isTerminal()) {
                last[0] = s;
                return true;
            }
            return false;
        }, interval);
        return last[0];
    }

    private static Path createScript(String workDir, List<String> cmd) throws IOException {
        Path dir = Path.of(workDir);
        Files.createDirectories(dir);
        Path scriptFile = Files.createTempFile(dir, "rudder_", ".sh",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));

        StringBuilder sb = new StringBuilder("#!/bin/bash\n");
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(shellQuote(cmd.get(i)));
        }
        sb.append('\n');

        Files.writeString(scriptFile, sb.toString());
        return scriptFile;
    }

    private static String shellQuote(String arg) {
        if (arg.matches("[a-zA-Z0-9_./:=@+,-]+")) {
            return arg;
        }
        return "'" + arg.replace("'", "'\\''") + "'";
    }
}
