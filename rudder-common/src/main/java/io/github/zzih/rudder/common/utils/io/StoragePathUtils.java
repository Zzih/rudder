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

package io.github.zzih.rudder.common.utils.io;

import io.github.zzih.rudder.common.RudderConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 统一的存储路径生成工具。
 * <p>
 * IDE 来源：{ws}/{date}/{prefix}/IDE/{SCRIPTS_DIR}/{scriptName}/{taskName}_{taskInstanceId}{ext}
 * 工作流来源：{ws}/{date}/{prefix}/{WORKFLOW_DEFINITIONS_DIR}/{workflowDefinitionCode}/{workflowInstanceId}/{taskName}_{taskInstanceId}{ext}
 */
public final class StoragePathUtils {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private StoragePathUtils() {
    }

    /**
     * 生成日志的 storage 相对路径。
     */
    public static String logPath(String workspaceName, String scriptName,
                                 Long workflowDefinitionCode, Long workflowInstanceId,
                                 String taskName, Long taskInstanceId) {
        return buildPath("logs", workspaceName, scriptName, workflowDefinitionCode, workflowInstanceId,
                taskName, taskInstanceId, ".log");
    }

    /**
     * 生成结果文件的 storage 相对路径。
     */
    public static String resultPath(String workspaceName, String scriptName,
                                    Long workflowDefinitionCode, Long workflowInstanceId,
                                    String taskName, Long taskInstanceId, String extension) {
        return buildPath("results", workspaceName, scriptName, workflowDefinitionCode, workflowInstanceId,
                taskName, taskInstanceId, extension);
    }

    /**
     * 从已有的 logPath 推导出结果文件路径（替换前缀和扩展名）。
     */
    public static String resultPathFromLogPath(String logPath, String extension) {
        if (logPath == null) {
            return null;
        }
        String path = logPath.replaceFirst("/logs/", "/results/");
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx > 0) {
            path = path.substring(0, dotIdx) + extension;
        } else {
            path = path + extension;
        }
        return path;
    }

    private static String buildPath(String prefix, String workspaceName,
                                    String scriptName, Long workflowDefinitionCode,
                                    Long workflowInstanceId, String taskName,
                                    Long taskInstanceId, String extension) {
        String ws = sanitize(workspaceName);
        String date = LocalDate.now().format(DATE_FMT);
        String fileName = sanitize(taskName) + "_" + taskInstanceId + extension;

        if (workflowDefinitionCode != null && workflowInstanceId != null) {
            return String.format("%s/%s/%s/%s/%d/%d/%s",
                    ws, date, prefix, RudderConstants.WORKFLOW_DEFINITIONS_DIR,
                    workflowDefinitionCode, workflowInstanceId, fileName);
        } else {
            return String.format("%s/%s/%s/IDE/%s/%s/%s",
                    ws, date, prefix, RudderConstants.SCRIPTS_DIR, sanitize(scriptName), fileName);
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "default";
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_");
    }
}
