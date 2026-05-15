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

package io.github.zzih.rudder.service.registry;

import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.exception.BizException;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 多 Server 并发拉同一任务的日志 / 结果时,确定性落到同一备份节点,避免 FileStorage 重复下载。 */
@Service
@RequiredArgsConstructor
public class NodeRouter {

    private final ServiceRegistryService serviceRegistryService;
    private final NodeSelector nodeSelector;

    public String pickFetchHost(long taskId, String originalHost) {
        List<NodeAddress> live = serviceRegistryService.getOnlineExecutions();
        if (live.isEmpty()) {
            throw new BizException(ScriptErrorCode.DISPATCH_NO_EXECUTION_NODE);
        }
        if (originalHost != null && live.stream().anyMatch(n -> n.rpcAddress().equals(originalHost))) {
            return originalHost;
        }
        return nodeSelector.select(taskId, live).rpcAddress();
    }
}
