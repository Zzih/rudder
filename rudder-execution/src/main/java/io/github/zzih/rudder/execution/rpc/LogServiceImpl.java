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

import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.rpc.service.ILogService;
import io.github.zzih.rudder.service.config.LogStorageService;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * worker 端 RPC 服务：API 节点拉日志时调用。文件 IO 委托给 {@link LogStorageService}。
 */
@Component
@RequiredArgsConstructor
public class LogServiceImpl implements ILogService {

    private final LogStorageService logStorageService;

    @Override
    public LogResponse fetchLog(String logPath, int offsetLine) {
        return logStorageService.readLog(logPath, offsetLine);
    }
}
