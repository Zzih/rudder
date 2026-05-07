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

package io.github.zzih.rudder.approval.api;

import io.github.zzih.rudder.approval.api.model.ApprovalCallbackResult;
import io.github.zzih.rudder.approval.api.model.ApprovalRequest;

import java.util.Map;

public interface ApprovalNotifier extends AutoCloseable {

    /** 健康检查。默认 UNKNOWN（provider 未实现）。 */
    default io.github.zzih.rudder.spi.api.model.HealthStatus healthCheck() {
        return io.github.zzih.rudder.spi.api.model.HealthStatus.unknown();
    }

    @Override
    default void close() {
    }

    /**
     * 渠道标识，如 "LOCAL"、"LARK"、"KISSFLOW"。
     */
    String getProvider();

    /**
     * 提交审批请求并返回外部审批 ID。
     */
    String submitApproval(ApprovalRequest request);

    /**
     * 处理外部回调（原始 HTTP body + headers）。
     * 各渠道自行解析各自的回调格式，返回统一的 ApprovalCallbackResult。
     */
    ApprovalCallbackResult handleCallback(String rawBody, Map<String, String> headers);
}
