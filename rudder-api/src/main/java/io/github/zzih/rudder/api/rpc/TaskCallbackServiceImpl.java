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

package io.github.zzih.rudder.api.rpc;

import io.github.zzih.rudder.common.execution.TaskCompletionReport;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.rpc.service.ITaskCallbackService;
import io.github.zzih.rudder.service.workflow.executor.WorkflowExecutor;
import io.github.zzih.rudder.service.workflow.executor.event.NodeCompletionEvent;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务完成回调 RPC 服务实现。
 * <p>
 * 接收执行节点的任务完成通知，转发给对应的 WorkflowInstanceRunner。
 * 替代原来的 DB 轮询机制（pollTaskCompletions）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCallbackServiceImpl implements ITaskCallbackService {

    private final WorkflowExecutor workflowExecutor;

    @Override
    public void onTaskCompleted(TaskCompletionReport report) {
        log.info("Task completion callback: taskInstanceId={}, status={}",
                report.getTaskInstanceId(), report.getStatus());

        if (report.getWorkflowInstanceId() == null || report.getTaskDefinitionCode() == null) {
            // IDE 直执的任务，没有工作流上下文
            return;
        }

        InstanceStatus status;
        try {
            status = InstanceStatus.valueOf(report.getStatus());
        } catch (Exception e) {
            log.warn("Unknown status in callback: {}", report.getStatus());
            return;
        }

        workflowExecutor.notifyTaskCompletion(new NodeCompletionEvent(
                report.getTaskDefinitionCode(),
                report.getTaskInstanceId(),
                status,
                report.getErrorMessage(),
                report.getWorkflowInstanceId(),
                report.getVarPool()));
    }
}
