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

package io.github.zzih.rudder.service.workflow.executor.event;

import io.github.zzih.rudder.dao.enums.InstanceStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 任务完成事件，由 TaskWorker 回调或轮询产生，放入 WorkflowInstanceRunner 的 completionQueue。
 *
 * <p>{@code varPoolJson} 为可选字段：RPC 回调路径上 Execution 端直接回传，runner 即可 merge 免一次 SELECT；
 * 兜底轮询路径没有这份数据，runner 会退化到从 DB 补读 task_instance 行。
 */
@Data
@AllArgsConstructor
public class NodeCompletionEvent {

    private Long taskCode;
    private Long taskInstanceId;
    private InstanceStatus status;
    private String errorMessage;

    /** 该任务所属的工作流实例 ID，供 {@code WorkflowExecutor} 精准路由到对应 runner。 */
    private Long workflowInstanceId;

    /** Execution 端 TaskPipeline 结束时产出的 varPool JSON；null 表示调用方没带，runner 自行从 DB 读。 */
    private String varPoolJson;
}
