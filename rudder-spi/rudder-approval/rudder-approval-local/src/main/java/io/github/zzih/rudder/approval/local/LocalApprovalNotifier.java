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

package io.github.zzih.rudder.approval.local;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.model.ApprovalCallback;
import io.github.zzih.rudder.approval.api.model.ApprovalCallbackResult;
import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地审批 channel：使用 Rudder 自身的"在 UI 点通过/拒绝"流程。
 * <p>
 * 仅做通信适配（生成 externalId / 解析 callback），平台索引由 service 层统一写。
 */
@Slf4j
public class LocalApprovalNotifier implements ApprovalNotifier {

    public static final String CHANNEL = "LOCAL";

    @Override
    public String getProvider() {
        return CHANNEL;
    }

    @Override
    public String submitApproval(ApprovalRequest request) {
        return UUID.randomUUID().toString();
    }

    @Override
    public ApprovalCallbackResult handleCallback(String rawBody, Map<String, String> headers) {
        ApprovalCallback callback = JsonUtils.fromJson(rawBody, ApprovalCallback.class);
        if (callback == null) {
            log.warn("Failed to parse local approval callback body");
            return ApprovalCallbackResult.empty();
        }
        callback.setChannel(CHANNEL);
        return ApprovalCallbackResult.ofCallback(callback);
    }
}
