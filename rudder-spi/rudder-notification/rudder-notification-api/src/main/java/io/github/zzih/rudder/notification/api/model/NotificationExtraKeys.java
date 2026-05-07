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

/**
 * NotificationMessage.extra 中使用的标准 key 常量。
 */
public final class NotificationExtraKeys {

    private NotificationExtraKeys() {
    }

    // ==================== 通用 ====================

    public static final String EVENT_TYPE = "eventType";
    public static final String DETAIL_URL = "detailUrl";
    /** 逗号分隔的用户 ID 列表 */
    public static final String AT_USERS = "atUsers";

    // ==================== APPROVAL ====================

    public static final String ACTION = "action";
    public static final String WORKFLOW_NAME = "workflowName";
    public static final String WORKFLOW_CODE = "workflowCode";
    public static final String REMARK = "remark";

    // ==================== NODE_OFFLINE ====================

    public static final String NODE_COUNT = "nodeCount";

    public static String nodeType(int index) {
        return "node." + index + ".type";
    }

    public static String nodeHost(int index) {
        return "node." + index + ".host";
    }

    public static String nodePort(int index) {
        return "node." + index + ".port";
    }
}
