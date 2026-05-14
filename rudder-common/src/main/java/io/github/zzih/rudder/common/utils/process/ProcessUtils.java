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

package io.github.zzih.rudder.common.utils.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.MDC;

public class ProcessUtils {

    private ProcessUtils() {
    }

    public static Result execute(String[] command, Consumer<String> lineConsumer,
                                 long timeoutSec) throws IOException, InterruptedException {
        return execute(command, null, lineConsumer, timeoutSec);
    }

    public static Result execute(String[] command,
                                 Consumer<String> lineConsumer) throws IOException, InterruptedException {
        return execute(command, null, lineConsumer, 0);
    }

    public static Result execute(String[] command, Map<String, String> env, Consumer<String> lineConsumer,
                                 long timeoutSec) throws IOException, InterruptedException {
        Process process = builder(command, env).start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
        }

        boolean finished;
        if (timeoutSec > 0) {
            finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        } else {
            process.waitFor();
            finished = true;
        }

        if (!finished) {
            process.destroyForcibly();
            return new Result(-1, true);
        }

        return new Result(process.exitValue(), false);
    }

    public static RunningProcess start(String[] command, Consumer<String> lineConsumer) throws IOException {
        return start(command, null, lineConsumer);
    }

    public static RunningProcess start(String[] command, Map<String, String> env,
                                       Consumer<String> lineConsumer) throws IOException {
        Process process = builder(command, env).start();

        // reader 是新建的 daemon 线程,默认不继承 MDC;显式拷贝调用方 context,
        // 否则 lineConsumer 内 log.info 的行无法按 taskLogPath 路由到任务日志文件。
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        Thread reader = new Thread(() -> {
            if (callerMdc != null) {
                MDC.setContextMap(callerMdc);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (lineConsumer != null) {
                        lineConsumer.accept(line);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                MDC.clear();
            }
        }, "process-reader-" + process.pid());
        reader.setDaemon(true);
        reader.start();

        return new RunningProcess(process, reader);
    }

    private static ProcessBuilder builder(String[] command, Map<String, String> env) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        return pb;
    }

    /**
     * 执行结果。
     *
     * @param exitCode  进程退出码，超时时为 -1
     * @param timedOut  是否超时被强制终止
     */
    public record Result(int exitCode, boolean timedOut) {

        public boolean isSuccess() {
            return exitCode == 0 && !timedOut;
        }
    }

    /**
     * 运行中的进程句柄，支持等待完成和强制终止。
     */
    public static class RunningProcess {

        private final Process process;
        private final Thread readerThread;

        RunningProcess(Process process, Thread readerThread) {
            this.process = process;
            this.readerThread = readerThread;
        }

        public Result waitFor(long timeoutSec) throws InterruptedException {
            boolean finished;
            if (timeoutSec > 0) {
                finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }
            readerThread.join(2000);
            if (!finished) {
                process.destroyForcibly();
                return new Result(-1, true);
            }
            return new Result(process.exitValue(), false);
        }

        public void destroy() {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        public boolean isAlive() {
            return process.isAlive();
        }
    }
}
