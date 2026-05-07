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

package io.github.zzih.rudder.execution.rpc;

import io.github.zzih.rudder.common.execution.TaskDispatchRequest;
import io.github.zzih.rudder.execution.worker.TaskWorker;
import io.github.zzih.rudder.rpc.service.ITaskExecutionService;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行 RPC 服务实现。替代原来的 TaskExecutionController。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskExecutionServiceImpl implements ITaskExecutionService {

    private final TaskWorker taskWorker;

    @Override
    public void dispatch(TaskDispatchRequest request) {
        log.info("RPC dispatch: taskInstanceId={}", request.getTaskInstanceId());
        taskWorker.submitTask(request.getTaskInstanceId(), request.getCallbackAddress());
    }

    @Override
    public void cancel(Long taskInstanceId) {
        log.info("RPC cancel: taskInstanceId={}", taskInstanceId);
        taskWorker.cancelTask(taskInstanceId);
    }

    @Override
    public String triggerSavepoint(Long taskInstanceId) {
        log.info("RPC triggerSavepoint: taskInstanceId={}", taskInstanceId);
        return taskWorker.triggerSavepoint(taskInstanceId);
    }
}
