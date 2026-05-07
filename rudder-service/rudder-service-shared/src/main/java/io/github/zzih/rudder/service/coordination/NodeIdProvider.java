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

package io.github.zzih.rudder.service.coordination;

import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本节点唯一标识。用于 pub/sub 消息过滤("自己广播的自己忽略")。
 * 优先走 {@code rudder.node-id} 配置,否则 {@code hostname-pid} 兜底。
 */
@Component
public class NodeIdProvider {

    private final String nodeId;

    public NodeIdProvider(@Value("${rudder.node-id:#{null}}") String configured) {
        this.nodeId = (configured != null && !configured.isBlank()) ? configured : computeDefault();
    }

    public String nodeId() {
        return nodeId;
    }

    private static String computeDefault() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignore) {
            host = "unknown";
        }
        return host + "-" + CodeGenerateUtils.getProcessID();
    }
}
