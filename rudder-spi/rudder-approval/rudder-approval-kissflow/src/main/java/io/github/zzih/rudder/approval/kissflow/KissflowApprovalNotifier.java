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

package io.github.zzih.rudder.approval.kissflow;

import static io.github.zzih.rudder.approval.api.model.ApprovalExtraKeys.INITIATOR_EMAIL;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.model.ApprovalAction;
import io.github.zzih.rudder.approval.api.model.ApprovalCallback;
import io.github.zzih.rudder.approval.api.model.ApprovalCallbackResult;
import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Kissflow 审批通知器。
 * <p>
 * 提交审批：调用 Kissflow Process API 创建流程实例。
 * 处理回调：解析 Kissflow Webhook 推送的状态变更事件。
 */
@Slf4j
public class KissflowApprovalNotifier implements ApprovalNotifier {

    private final String apiKey;
    private final String accountId;
    private final String processId;
    private final String titleField;
    private final String contentField;
    private final String applicantField;
    /** 阶段名 → Kissflow 字段名（多阶段映射）。无配置时跳过该阶段填充。 */
    private final Map<String, String> stageFieldMapping;

    public KissflowApprovalNotifier(String apiKey, String accountId, String processId,
                                    String titleField, String contentField, String applicantField,
                                    Map<String, String> stageFieldMapping) {
        this.apiKey = apiKey;
        this.accountId = accountId;
        this.processId = processId;
        this.titleField = (titleField == null || titleField.isBlank()) ? "Title" : titleField;
        this.contentField = (contentField == null || contentField.isBlank()) ? "Description" : contentField;
        this.applicantField = applicantField;
        this.stageFieldMapping = stageFieldMapping == null ? Map.of() : Map.copyOf(stageFieldMapping);
    }

    @Override
    public String getProvider() {
        return "KISSFLOW";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String submitApproval(ApprovalRequest request) {
        String url = String.format(
                "https://%s.kissflow.com/api/1/process/%s/submit", accountId, processId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(titleField, request.getTitle());
        if (request.getContent() != null) {
            body.put(contentField, request.getContent());
        }

        // 申请人 email 优先使用 ApprovalRequest 字段，回退到 extra（兼容旧调用方）
        String initiatorEmail = request.getApplicantEmail();
        if (initiatorEmail == null || initiatorEmail.isBlank()) {
            Map<String, String> extra = request.getExtra();
            initiatorEmail = extra != null ? extra.get(INITIATOR_EMAIL) : null;
        }
        if (initiatorEmail != null && !initiatorEmail.isBlank()) {
            body.put("_created_by", initiatorEmail);
            if (applicantField != null && !applicantField.isBlank()) {
                body.put(applicantField, initiatorEmail);
            }
        }

        // 各阶段候选人 emails → 填到对应 Kissflow 字段
        Map<String, List<String>> stageCandidates = request.getStageCandidates();
        if (stageCandidates != null && !stageCandidates.isEmpty() && !stageFieldMapping.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : stageCandidates.entrySet()) {
                String stage = entry.getKey();
                String fieldName = stageFieldMapping.get(stage);
                if (fieldName == null || fieldName.isBlank()) {
                    log.warn("Kissflow approval stage '{}' has no field mapping, skipping", stage);
                    continue;
                }
                List<String> emails = entry.getValue();
                if (emails == null || emails.isEmpty()) {
                    log.warn("Kissflow approval stage '{}' has empty candidates", stage);
                    continue;
                }
                body.put(fieldName, emails);
            }
        }

        String json = JsonUtils.toJson(body);
        log.debug("Kissflow create process instance request: {}", json);

        String resp = HttpUtils.postJson(url, json,
                Map.of("Authorization", "Bearer " + apiKey));

        Map<String, Object> result = JsonUtils.fromJson(resp, Map.class);
        String instanceId = (String) result.get("_id");
        if (instanceId == null) {
            instanceId = (String) result.get("Id");
        }
        if (instanceId == null) {
            throw new RuntimeException(
                    "Failed to create Kissflow process instance: " + resp);
        }

        log.info("Created Kissflow process instance: {}", instanceId);
        return instanceId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApprovalCallbackResult handleCallback(String rawBody, Map<String, String> headers) {
        Map<String, Object> event = JsonUtils.fromJson(rawBody, Map.class);
        if (event == null) {
            log.warn("Failed to parse Kissflow callback body");
            return ApprovalCallbackResult.empty();
        }

        String instanceId = (String) event.get("_id");
        if (instanceId == null) {
            instanceId = (String) event.get("Id");
        }
        String status = (String) event.get("_current_step");
        if (status == null) {
            status = (String) event.get("Status");
        }
        String approver = (String) event.get("_last_action_performed_by");

        if (instanceId == null || status == null) {
            log.debug("Ignoring Kissflow event: missing instanceId or status");
            return ApprovalCallbackResult.empty();
        }

        ApprovalAction action;
        String normalizedStatus = status.toUpperCase();
        if (normalizedStatus.contains("APPROVED") || normalizedStatus.contains("COMPLETED")) {
            action = ApprovalAction.APPROVED;
        } else if (normalizedStatus.contains("REJECTED") || normalizedStatus.contains("DENIED")) {
            action = ApprovalAction.REJECTED;
        } else {
            log.debug("Ignoring Kissflow status: {}", status);
            return ApprovalCallbackResult.empty();
        }

        ApprovalCallback callback = new ApprovalCallback();
        callback.setChannel("KISSFLOW");
        callback.setExternalApprovalId(instanceId);
        callback.setAction(action);
        callback.setApprover(approver);

        log.info("Kissflow approval callback: instance={}, status={}, approver={}",
                instanceId, status, approver);
        return ApprovalCallbackResult.ofCallback(callback);
    }
}
