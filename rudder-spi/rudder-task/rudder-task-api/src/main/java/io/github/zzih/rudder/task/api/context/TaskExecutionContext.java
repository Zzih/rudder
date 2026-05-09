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

package io.github.zzih.rudder.task.api.context;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionContext {

    private Long taskInstanceId;
    private String taskName;
    private TaskType taskType;
    private String paramsJson;
    private DataSourceInfo dataSourceInfo;
    private Map<String, String> resolvedFilePaths;
    private String executePath;
    private int timeoutSeconds;
    private int retryTimes;

    /**
     * 解析后的 prepareParams,SQL 任务用 PreparedStatement 绑定 ${var}。
     * 包含 project / global / runtime / local IN / built-in 各层合并后的最终视图,
     * Worker 在派发前装填。Shell/Python 等非 SQL 任务可忽略 — 它们已用字符串替换处理过。
     */
    private List<Property> prepareParams;

    /**
     * 任务的 OUT 参数声明白名单(对齐 DolphinScheduler 的 localParams Direct.OUT)。
     * 来自 {@code task_definition.output_params}。Task 内部 {@code dealOutParam} 时按这份过滤:
     * 没声明的 prop 一律不进 varPool,防止上游意外污染下游。空列表 / null 都视为无 OUT 声明,
     * Task 直接产出空 varPool。
     */
    private List<Property> outputParamsSpec;

    /**
     * 执行模式：BATCH 或 STREAMING。
     */
    private String executionMode;

    /**
     * 工作流实例 ID，临时执行时为 {@code null}。
     */
    private Long workflowInstanceId;

    /** Active runtime 暴露的子进程环境变量,由 Worker 在 task.init 前从 RuntimeConfigService 灌入。 */
    private Map<String, String> envVars;
}
