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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 进程执行工具类，封装 ProcessBuilder + BufferedReader + waitFor 的通用模式。
 */
public class ProcessUtils {

    private ProcessUtils() {
    }

    /**
     * 执行命令并逐行读取输出。
     *
     * @param command      命令参数
     * @param lineConsumer 每行输出回调（可用于日志记录或输出参数解析）
     * @param timeoutSec   超时秒数，0 或负数表示无限等待
     * @return 执行结果
     */
    public static Result execute(String[] command, Consumer<String> lineConsumer,
                                 long timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

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

    /**
     * 执行命令，无超时限制。
     */
    public static Result execute(String[] command,
                                 Consumer<String> lineConsumer) throws IOException, InterruptedException {
        return execute(command, lineConsumer, 0);
    }

    /**
     * 启动命令并返回 RunningProcess 句柄，调用方可持有引用用于 cancel。
     * 调用方需自行调用 {@link RunningProcess#waitFor} 等待完成。
     */
    public static RunningProcess start(String[] command, Consumer<String> lineConsumer) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 在后台线程读取输出，避免阻塞
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (lineConsumer != null) {
                        lineConsumer.accept(line);
                    }
                }
            } catch (IOException ignored) {
            }
        }, "process-reader-" + process.pid());
        reader.setDaemon(true);
        reader.start();

        return new RunningProcess(process, reader);
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
