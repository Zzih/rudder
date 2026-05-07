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

package io.github.zzih.rudder.task.api.log;

import org.slf4j.MDC;

/**
 * 用于设置/清除按任务路由日志的 MDC 上下文的工具类。
 * <p>
 * 用法：
 * <pre>
 * try (var ignored = TaskLogUtils.withTaskLog(taskInstanceId, logPath)) {
 *     // 此线程上的所有 log.info/warn/error 都写入任务的日志文件
 *     task.init();
 *     task.handle();
 * }
 * </pre>
 */
public final class TaskLogUtils {

    public static final String MDC_TASK_LOG_PATH = "taskLogPath";
    public static final String MDC_TASK_INSTANCE_ID = "taskInstanceId";

    private TaskLogUtils() {
    }

    /**
     * 设置用于任务日志路由的 MDC key。返回一个 AutoCloseable 用于清除这些 key。
     */
    public static MDCAutoCloseable withTaskLog(Long taskInstanceId, String logPath) {
        MDC.put(MDC_TASK_LOG_PATH, logPath);
        MDC.put(MDC_TASK_INSTANCE_ID, String.valueOf(taskInstanceId));
        return () -> {
            MDC.remove(MDC_TASK_LOG_PATH);
            MDC.remove(MDC_TASK_INSTANCE_ID);
        };
    }

    @FunctionalInterface
    public interface MDCAutoCloseable extends AutoCloseable {

        @Override
        void close(); // 无受检异常
    }
}
