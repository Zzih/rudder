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

import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.rpc.annotation.RpcMethod;
import io.github.zzih.rudder.rpc.annotation.RpcService;

/**
 * 日志读取 RPC 服务。Execution Node 和 API Server 都实现此接口。
 * <p>
 * 谁的文件系统上有日志文件，谁就提供此服务。
 */
@RpcService
public interface ILogService {

    @RpcMethod
    LogResponse fetchLog(String logPath, int offsetLine);
}
