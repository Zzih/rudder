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

package io.github.zzih.rudder.service.script;

import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.common.execution.ResultResponse;
import io.github.zzih.rudder.common.execution.TaskDispatchRequest;
import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.rpc.client.Clients;
import io.github.zzih.rudder.rpc.client.RpcClient;
import io.github.zzih.rudder.rpc.service.ILogService;
import io.github.zzih.rudder.rpc.service.IResultService;
import io.github.zzih.rudder.rpc.service.ITaskExecutionService;
import io.github.zzih.rudder.service.registry.ServiceRegistryService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通过 RPC 将任务从 Server 节点分发到 Execution 节点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private final ServiceRegistryService registryService;
    private final RpcClient rpcClient;

    /**
     * 选择一个可用的执行节点并分发任务。
     * <p>
     * 节点已由 mapper 按 {@code task_count ASC, heartbeat DESC} 预排序，
     * 本方法在 {@code task_count} 最小的若干节点里随机挑选一个，避免并发派发全部打到
     * 排序首位的单节点（经典的雷群效应）。
     *
     * @return 执行节点的 RPC 地址（host:rpcPort）
     */
    public String dispatch(Long taskInstanceId) {
        List<ServiceRegistry> nodes = registryService.getOnlineExecutions();
        if (nodes.isEmpty()) {
            throw new BizException(ScriptErrorCode.DISPATCH_NO_EXECUTION_NODE);
        }

        ServiceRegistry node = pickLeastLoaded(nodes);
        String rpcAddress = node.getHost() + ":" + node.getPort();
        String callbackAddress = registryService.getLocalRpcAddress();

        log.info("Dispatching task {} to execution node {}", taskInstanceId, rpcAddress);

        try {
            ITaskExecutionService service = Clients.create(rpcClient, ITaskExecutionService.class, rpcAddress);
            TaskDispatchRequest request = new TaskDispatchRequest();
            request.setTaskInstanceId(taskInstanceId);
            request.setCallbackAddress(callbackAddress);
            service.dispatch(request);
        } catch (Exception e) {
            log.error("Failed to dispatch task {} to {}", taskInstanceId, rpcAddress, e);
            throw new BizException(ScriptErrorCode.DISPATCH_FAILED, e.getMessage());
        }

        return rpcAddress;
    }

    /**
     * 在 task_count 最小的候选节点中随机挑选一个。nodes 已按 task_count 升序排好。
     */
    private ServiceRegistry pickLeastLoaded(List<ServiceRegistry> nodes) {
        if (nodes.size() == 1) {
            return nodes.get(0);
        }
        int minCount = nodes.get(0).getTaskCount() != null ? nodes.get(0).getTaskCount() : 0;
        List<ServiceRegistry> candidates = new ArrayList<>();
        for (ServiceRegistry n : nodes) {
            int count = n.getTaskCount() != null ? n.getTaskCount() : 0;
            if (count != minCount) {
                break;
            }
            candidates.add(n);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * 将取消请求转发到执行节点。
     */
    public void cancelOnExecution(String executionHost, Long taskInstanceId) {
        if (executionHost == null) {
            return;
        }
        try {
            ITaskExecutionService service = Clients.create(rpcClient, ITaskExecutionService.class, executionHost);
            service.cancel(taskInstanceId);
        } catch (Exception e) {
            log.warn("Failed to cancel task {} on {}: {}", taskInstanceId, executionHost, e.getMessage());
        }
    }

    /**
     * 将 Savepoint 请求转发到执行节点。
     */
    public String triggerSavepoint(String executionHost, Long taskInstanceId) {
        log.info("转发Savepoint请求, taskInstanceId={}, executionHost={}", taskInstanceId, executionHost);
        if (executionHost == null) {
            throw new RuntimeException("No execution host for savepoint");
        }
        try {
            ITaskExecutionService service = Clients.create(rpcClient, ITaskExecutionService.class, executionHost);
            return service.triggerSavepoint(taskInstanceId);
        } catch (Exception e) {
            log.error("Savepoint触发失败, taskInstanceId={}, host={}", taskInstanceId, executionHost, e);
            throw new RuntimeException("Failed to trigger savepoint on " + executionHost + ": " + e.getMessage(), e);
        }
    }

    /**
     * 通过 RPC 从目标节点增量获取日志内容。
     */
    public LogResponse fetchLog(String executionHost, String logPath, int offsetLine) {
        if (executionHost == null || logPath == null) {
            return new LogResponse("", offsetLine);
        }
        try {
            ILogService logService = Clients.create(rpcClient, ILogService.class, executionHost);
            return logService.fetchLog(logPath, offsetLine);
        } catch (Exception e) {
            log.warn("Failed to fetch log from {}: {}", executionHost, e.getMessage());
            return new LogResponse("Failed to fetch log from " + executionHost, offsetLine);
        }
    }

    /**
     * 通过 RPC 从目标节点分页读取任务结果。
     */
    public ResultResponse fetchResult(String executionHost, String resultPath, int offset, int limit) {
        if (executionHost == null || resultPath == null) {
            return new ResultResponse(java.util.List.of(), java.util.List.of(), 0, offset, limit);
        }
        try {
            IResultService resultService = Clients.create(rpcClient, IResultService.class, executionHost);
            return resultService.fetchResult(resultPath, offset, limit);
        } catch (Exception e) {
            log.warn("Failed to fetch result from {}: {}", executionHost, e.getMessage());
            return new ResultResponse(java.util.List.of(), java.util.List.of(), 0, offset, limit);
        }
    }
}
