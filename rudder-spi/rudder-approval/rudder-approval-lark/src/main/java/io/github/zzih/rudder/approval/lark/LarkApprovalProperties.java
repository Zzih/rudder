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

package io.github.zzih.rudder.approval.lark;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Lark 审批配置。{@code stageFieldMapping} 在 admin UI 上是 JSON 文本输入,但 Jackson 反序列化时
 * 字段类型是 {@code Map<String, String>} —— 前端要把 textarea JSON 文本解析后再放进 providerParams 提交。
 */
public record LarkApprovalProperties(
        String appId,
        String appSecret,
        String approvalCode,
        String titleWidgetId,
        String contentWidgetId,
        String applicantWidgetId,
        @JsonAlias("stageFieldMapping") Map<String, String> stageFieldMapping,
        String encryptKey,
        String verificationToken) {

    public LarkApprovalProperties {
        if (appId == null) {
            appId = "";
        }
        if (appSecret == null) {
            appSecret = "";
        }
        if (approvalCode == null) {
            approvalCode = "";
        }
        if (titleWidgetId == null || titleWidgetId.isBlank()) {
            titleWidgetId = "title";
        }
        if (contentWidgetId == null || contentWidgetId.isBlank()) {
            contentWidgetId = "content";
        }
        if (applicantWidgetId == null) {
            applicantWidgetId = "";
        }
        if (stageFieldMapping == null) {
            stageFieldMapping = Map.of();
        }
        if (encryptKey == null) {
            encryptKey = "";
        }
        if (verificationToken == null) {
            verificationToken = "";
        }
    }
}
