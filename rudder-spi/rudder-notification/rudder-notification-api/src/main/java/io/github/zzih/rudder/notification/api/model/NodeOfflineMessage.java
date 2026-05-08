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

package io.github.zzih.rudder.notification.api.model;

import java.util.List;

import lombok.Builder;

/** graceful=true 是 PreDestroy 优雅下线；false 是心跳超时被强制翻转（运维告警）。 */
@Builder
public record NodeOfflineMessage(
        NotificationLevel level,
        List<NodeInfo> nodes,
        boolean graceful,
        List<UserRef> oncall) implements NotificationMessage {

    public NodeOfflineMessage {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        oncall = oncall == null ? List.of() : List.copyOf(oncall);
    }

    @Override
    public NotificationEventType eventType() {
        return NotificationEventType.NODE_OFFLINE;
    }
}
