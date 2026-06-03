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
 * 提交审批：先以表单字段建实例草稿，再 submit 进入流程（Kissflow Process v2 API 分两步）。
 * 处理回调：解析 Kissflow Webhook 推送的工作流事件。
 */
@Slf4j
public class KissflowApprovalNotifier implements ApprovalNotifier {

    static final String CHANNEL = "KISSFLOW";

    // Kissflow access key 鉴权：key id 明文 + secret 成对走 header，非 Bearer。
    private static final String HEADER_ACCESS_KEY_ID = "X-Access-Key-Id";
    private static final String HEADER_ACCESS_KEY_SECRET = "X-Access-Key-Secret";

    // 约定字段 ID：流程表单须按此命名，Rudder 直接以这些 ID 写入。阶段候选人字段 ID 等于阶段标识。
    private static final String FIELD_TITLE = "Title";
    private static final String FIELD_CONTENT = "Description";
    private static final String FIELD_APPLICANT = "Applicant";

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String accountId;
    private final String domain;
    private final String processId;

    public KissflowApprovalNotifier(String accessKeyId, String accessKeySecret, String accountId, String domain,
                                    String processId) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.accountId = accountId;
        // 账户域名独立于 accountId：API 主机用登录子域名，路径用 account_id，两者可不同。
        this.domain = normalizeDomain(domain);
        this.processId = processId;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        return domain.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    @Override
    public String getProvider() {
        return CHANNEL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String submitApproval(ApprovalRequest request) {
        Map<String, Object> fields = buildFields(request);

        String createUrl = String.format("%s/process/2/%s/%s", baseUrl(), accountId, processId);
        String createResp = HttpUtils.postJson(createUrl, JsonUtils.toJson(fields), authHeaders());
        Map<String, Object> created = JsonUtils.fromJson(createResp, Map.class);

        String instanceId = firstString(created, "InstanceId", "_id");
        String activityInstanceId = firstString(created, "ActivityInstanceId", "_activity_instance_id");
        if (instanceId == null || activityInstanceId == null) {
            throw new RuntimeException(
                    "Kissflow create instance returned no InstanceId/ActivityInstanceId: " + createResp);
        }

        String submitUrl = String.format("%s/process/2/%s/%s/%s/%s/submit",
                baseUrl(), accountId, processId, instanceId, activityInstanceId);
        HttpUtils.postJson(submitUrl, "{}", authHeaders());

        log.info("Submitted Kissflow process instance: {}", instanceId);
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

        // Kissflow 无固定审批结果 webhook，回调由流程内 HTTP connector 按约定 body 外发。
        String instanceId = firstString(event, "instanceId", "InstanceId", "_id");
        String approver = firstString(event, "approver", "_last_action_performed_by");
        String signal = firstString(event, "action", "status", "Status");
        if (instanceId == null || signal == null) {
            log.debug("Ignoring Kissflow event: missing instanceId or action");
            return ApprovalCallbackResult.empty();
        }

        ApprovalAction action = toAction(signal);
        if (action == null) {
            log.debug("Ignoring Kissflow signal: {}", signal);
            return ApprovalCallbackResult.empty();
        }

        ApprovalCallback callback = new ApprovalCallback();
        callback.setChannel(CHANNEL);
        callback.setExternalApprovalId(instanceId);
        callback.setAction(action);
        callback.setApprover(approver);

        log.info("Kissflow approval callback: instance={}, signal={}, approver={}",
                instanceId, signal, approver);
        return ApprovalCallbackResult.ofCallback(callback);
    }

    private Map<String, Object> buildFields(ApprovalRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(FIELD_TITLE, request.getTitle());
        if (request.getContent() != null) {
            fields.put(FIELD_CONTENT, request.getContent());
        }

        String email = applicantEmail(request);
        if (email != null) {
            fields.put(FIELD_APPLICANT, email);
        }

        // 约定：阶段候选人字段 ID 等于阶段标识（ApprovalLevel.name()），key 直接作字段名写入。
        Map<String, List<String>> stageCandidates = request.getStageCandidates();
        if (stageCandidates != null) {
            stageCandidates.forEach((stage, emails) -> {
                if (emails == null || emails.isEmpty()) {
                    log.warn("Kissflow approval stage '{}' has empty candidates", stage);
                } else {
                    fields.put(stage, emails);
                }
            });
        }
        return fields;
    }

    private static String applicantEmail(ApprovalRequest request) {
        String email = request.getApplicantEmail();
        if (email == null || email.isBlank()) {
            Map<String, String> extra = request.getExtra();
            email = extra != null ? extra.get(INITIATOR_EMAIL) : null;
        }
        return (email == null || email.isBlank()) ? null : email;
    }

    private String baseUrl() {
        return "https://" + domain;
    }

    private Map<String, String> authHeaders() {
        return Map.of(HEADER_ACCESS_KEY_ID, accessKeyId, HEADER_ACCESS_KEY_SECRET, accessKeySecret);
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static ApprovalAction toAction(String signal) {
        String s = signal.toUpperCase();
        if (s.contains("APPROV") || s.contains("COMPLETED") || s.contains("DONE")) {
            return ApprovalAction.APPROVED;
        }
        if (s.contains("REJECT") || s.contains("DENIED")) {
            return ApprovalAction.REJECTED;
        }
        return null;
    }
}
