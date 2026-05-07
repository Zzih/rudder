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

package io.github.zzih.rudder.approval.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批回调处理结果。
 * <p>
 * callback 非空表示这是一个审批决策事件，需要触发业务回调。
 * directResponse 非空表示需要直接返回给调用方的响应（如飞书 URL 验证 challenge）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCallbackResult {

    /** 解析后的审批回调（可空，仅审批决策事件时有值） */
    private ApprovalCallback callback;

    /** 需要直接返回给调用方的响应体（可空，如飞书 url_verification） */
    private Object directResponse;

    public static ApprovalCallbackResult ofCallback(ApprovalCallback callback) {
        return new ApprovalCallbackResult(callback, null);
    }

    public static ApprovalCallbackResult ofDirectResponse(Object response) {
        return new ApprovalCallbackResult(null, response);
    }

    private static final ApprovalCallbackResult EMPTY = new ApprovalCallbackResult(null, null);

    public static ApprovalCallbackResult empty() {
        return EMPTY;
    }

    public boolean hasCallback() {
        return callback != null;
    }

    public boolean hasDirectResponse() {
        return directResponse != null;
    }
}
