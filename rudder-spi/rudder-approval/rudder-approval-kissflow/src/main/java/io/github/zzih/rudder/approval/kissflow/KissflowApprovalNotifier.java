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
import io.github.zzih.rudder.approval.api.model.StageDecision;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
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

    // Kissflow 流程的固定审批节点顺序；反查 progress 时按位置把 UserTask 步骤映射到阶段标识，
    // 未用到的级在流程中被跳过但仍以 Skipped 步骤占位，故位置始终对齐。
    private static final List<String> STAGES = List.of("PROJECT_OWNER", "WORKSPACE_OWNER", "SUPER_ADMIN");

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
        // 回调只带 instanceId+action；逐级审批人+时间反查 progress 时间线（连接器无法转发嵌套数组）。
        callback.setStageDecisions(fetchStageDecisions(instanceId, action));

        log.info("Kissflow approval callback: instance={}, signal={}, approver={}",
                instanceId, signal, approver);
        return ApprovalCallbackResult.ofCallback(callback);
    }

    // 反查 progress：UserTask 步骤按顺序对应 STAGES（被跳过的占位但不产生决议），取实际操作过步骤的操作人+时间。
    // 流程仅在批准时推进，故除终态级外每级均为批准；终态级承载整单结果（驳回时即驳回人那一级）。
    @SuppressWarnings("unchecked")
    private List<StageDecision> fetchStageDecisions(String instanceId, ApprovalAction outcome) {
        String url = String.format("%s/process/2/%s/%s/%s/progress", baseUrl(), accountId, processId, instanceId);
        List<StageDecision> decisions = new ArrayList<>();
        try {
            Map<String, Object> progress = JsonUtils.fromJson(HttpUtils.get(url, authHeaders()), Map.class);
            if (progress == null || !(progress.get("Steps") instanceof List<?> steps)) {
                return decisions;
            }
            int stageIdx = 0;
            for (Object item : steps) {
                if (!(item instanceof Map<?, ?> step) || !"UserTask".equals(step.get("NodeType"))) {
                    continue;
                }
                String stage = stageIdx < STAGES.size() ? STAGES.get(stageIdx) : null;
                stageIdx++;
                // 被跳过的级无 ActedBy；有操作人才算一级真实决议（批准或驳回皆有人操作）。
                String approver = actedByEmail(step.get("ActedBy"));
                if (stage != null && approver != null) {
                    decisions.add(new StageDecision(stage, approver,
                            parseActedAt(step.get("ActedAt")), ApprovalAction.APPROVED));
                }
            }
            if (outcome == ApprovalAction.REJECTED && !decisions.isEmpty()) {
                StageDecision terminal = decisions.get(decisions.size() - 1);
                decisions.set(decisions.size() - 1, new StageDecision(terminal.stage(),
                        terminal.approver(), terminal.decidedAt(), ApprovalAction.REJECTED));
            }
        } catch (RuntimeException ex) {
            log.warn("Kissflow progress fetch failed for instance '{}': {}", instanceId, ex.getMessage());
        }
        return decisions;
    }

    // 操作人身份取邮箱供 Rudder 关联用户：ActedBy 直接带 Email 则用，否则按 _id 反查用户详情，均无则回退显示名。
    private String actedByEmail(Object actedBy) {
        if (!(actedBy instanceof List<?> actors) || actors.isEmpty()
                || !(actors.get(0) instanceof Map<?, ?> actor)) {
            return null;
        }
        if (actor.get("Email") instanceof String email && !email.isBlank()) {
            return email;
        }
        if (actor.get("_id") instanceof String userId && !userId.isBlank()) {
            String email = resolveEmailByUserId(userId);
            if (email != null) {
                return email;
            }
        }
        return actor.get("Name") instanceof String name ? name : null;
    }

    private String resolveEmailByUserId(String userId) {
        String url = String.format("%s/user/2/%s/%s", baseUrl(), accountId, userId);
        try {
            String email = JsonUtils.extractValue(HttpUtils.get(url, authHeaders()), "Email");
            return email == null || email.isBlank() ? null : email;
        } catch (RuntimeException ex) {
            log.warn("Kissflow user email lookup failed for id '{}': {}", userId, ex.getMessage());
            return null;
        }
    }

    private static LocalDateTime parseActedAt(Object actedAt) {
        if (actedAt instanceof String s && !s.isBlank()) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault());
            } catch (RuntimeException ignored) {
                // Kissflow ActedAt 偶发缺失或非 ISO，缺时间不应阻断结单。
            }
        }
        return LocalDateTime.now();
    }

    private Map<String, Object> buildFields(ApprovalRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(FIELD_TITLE, request.getTitle());
        if (request.getContent() != null) {
            fields.put(FIELD_CONTENT, request.getContent());
        }

        // Kissflow 人员字段要求 {_id} 对象，且 _id 是用户内部 ID 非邮箱，须先按邮箱解析。
        Map<String, String> userIdCache = new HashMap<>();

        String email = applicantEmail(request);
        if (email != null) {
            List<Map<String, String>> refs = resolveRefs(List.of(email), userIdCache);
            if (!refs.isEmpty()) {
                fields.put(FIELD_APPLICANT, refs.get(0));
            }
        }

        // 约定：阶段候选人字段 ID 等于阶段标识（ApprovalLevel.name()），key 直接作字段名写入。
        Map<String, List<String>> stageCandidates = request.getStageCandidates();
        if (stageCandidates != null) {
            stageCandidates.forEach((stage, emails) -> {
                if (emails == null || emails.isEmpty()) {
                    log.warn("Kissflow approval stage '{}' has empty candidates", stage);
                    return;
                }
                List<Map<String, String>> refs = resolveRefs(emails, userIdCache);
                if (refs.isEmpty()) {
                    log.warn("Kissflow approval stage '{}' has no resolvable candidates", stage);
                } else {
                    fields.put(stage, refs);
                }
            });
        }
        return fields;
    }

    // 邮箱列表 → Kissflow 人员字段值 [{_id}]；解析不到的邮箱跳过并 warn。
    private List<Map<String, String>> resolveRefs(List<String> emails, Map<String, String> cache) {
        List<Map<String, String>> refs = new ArrayList<>();
        for (String email : emails) {
            String userId = cache.computeIfAbsent(email.toLowerCase(), k -> resolveUserId(email));
            if (userId == null) {
                log.warn("Kissflow user not found for email '{}', skipping", email);
            } else {
                refs.add(Map.of("_id", userId));
            }
        }
        return refs;
    }

    // Kissflow 人员字段的 _id 是用户内部 ID（非邮箱），按邮箱搜 user list 解析。
    private String resolveUserId(String email) {
        String url = String.format("%s/user/2/%s/?q=%s&page_size=2",
                baseUrl(), accountId, URLEncoder.encode(email, StandardCharsets.UTF_8));
        try {
            List<Map> users = JsonUtils.toList(HttpUtils.get(url, authHeaders()), Map.class);
            if (users.isEmpty()) {
                return null;
            }
            for (Map user : users) {
                if (user.get("Email") instanceof String e && e.equalsIgnoreCase(email)) {
                    return (String) user.get("_id");
                }
            }
            // 响应不含 Email 字段时，完整邮箱搜索应只命中目标用户。
            return users.size() == 1 ? (String) users.get(0).get("_id") : null;
        } catch (RuntimeException ex) {
            log.warn("Kissflow user lookup failed for '{}': {}", email, ex.getMessage());
            return null;
        }
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
