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

public enum NotificationEventType {

    /** 审批流转（提交审批、审批通过/驳回） */
    APPROVAL(false),

    /** Execution 节点上线通知 */
    NODE_ONLINE(true),

    /** Execution 节点离线告警 */
    NODE_OFFLINE(true);

    /** 是否为平台级事件（仅使用平台级配置，忽略 workspace 级配置） */
    private final boolean platformOnly;

    NotificationEventType(boolean platformOnly) {
        this.platformOnly = platformOnly;
    }

    public boolean isPlatformOnly() {
        return platformOnly;
    }
}
