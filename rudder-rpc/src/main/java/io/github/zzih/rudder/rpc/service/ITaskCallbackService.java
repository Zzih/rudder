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

package io.github.zzih.rudder.rpc.service;

import io.github.zzih.rudder.common.execution.TaskCompletionReport;
import io.github.zzih.rudder.rpc.annotation.RpcMethod;
import io.github.zzih.rudder.rpc.annotation.RpcService;

/**
 * 任务完成回调 RPC 服务，提供近实时的任务完成通知（替代 DB 轮询）。
 * <p>
 * 实现方：API Server（{@code rudder-api} 的 {@code TaskCallbackServiceImpl}），
 * 绑定在 API Server 的 RPC 端口上接收回调。
 * <p>
 * 调用方：Execution Node（{@code rudder-execution} 的 {@code TaskWorker}），
 * 任务完成（成功 / 失败 / 终止）后，向分派该任务的 API Server 回调上报。
 */
@RpcService
public interface ITaskCallbackService {

    @RpcMethod
    void onTaskCompleted(TaskCompletionReport report);
}
