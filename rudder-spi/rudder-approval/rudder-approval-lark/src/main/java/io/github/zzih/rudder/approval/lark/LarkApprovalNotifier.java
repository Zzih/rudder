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

import static io.github.zzih.rudder.approval.api.model.ApprovalExtraKeys.INITIATOR_EMAIL;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.model.ApprovalAction;
import io.github.zzih.rudder.approval.api.model.ApprovalCallback;
import io.github.zzih.rudder.approval.api.model.ApprovalCallbackResult;
import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.lark.oapi.Client;
import com.lark.oapi.service.approval.v4.model.CreateInstanceReq;
import com.lark.oapi.service.approval.v4.model.CreateInstanceResp;
import com.lark.oapi.service.approval.v4.model.InstanceCreate;
import com.lark.oapi.service.approval.v4.model.SubscribeApprovalReq;
import com.lark.oapi.service.approval.v4.model.SubscribeApprovalResp;
import com.lark.oapi.service.contact.v3.model.BatchGetIdUserReq;
import com.lark.oapi.service.contact.v3.model.BatchGetIdUserReqBody;
import com.lark.oapi.service.contact.v3.model.BatchGetIdUserResp;
import com.lark.oapi.service.contact.v3.model.UserContactInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * 飞书审批通知器（基于飞书官方 SDK）。
 * <p>
 * 提交审批：调用飞书审批 SDK 创建审批实例。
 * 处理回调：解析飞书事件订阅回调，支持 url_verification、v1.0/v2.0 事件格式。
 * 自动订阅：首次提交审批时自动调用飞书 subscribe API 激活事件推送。
 */
@Slf4j
public class LarkApprovalNotifier implements ApprovalNotifier {

    private final Client client;
    private final String approvalCode;
    private final String titleWidgetId;
    private final String contentWidgetId;
    private final String applicantWidgetId;
    /** 阶段名 → 飞书人员控件 widget id（多阶段映射）。无配置时跳过该阶段填充。 */
    private final Map<String, String> stageFieldMapping;
    private final String encryptKey;
    private final String verificationToken;

    private volatile boolean subscribed = false;

    public LarkApprovalNotifier(String appId, String appSecret, String approvalCode,
                                String titleWidgetId, String contentWidgetId,
                                String applicantWidgetId,
                                Map<String, String> stageFieldMapping,
                                String encryptKey, String verificationToken) {
        this.client = Client.newBuilder(appId, appSecret).build();
        this.approvalCode = approvalCode;
        this.titleWidgetId = titleWidgetId;
        this.contentWidgetId = contentWidgetId;
        this.applicantWidgetId = applicantWidgetId;
        this.stageFieldMapping = stageFieldMapping == null ? Map.of() : Map.copyOf(stageFieldMapping);
        this.encryptKey = encryptKey;
        this.verificationToken = verificationToken;
    }

    @Override
    public String getProvider() {
        return "LARK";
    }

    @Override
    public String submitApproval(ApprovalRequest request) {
        ensureSubscribed();

        List<Map<String, Object>> formItems = new ArrayList<>();
        formItems.add(Map.of("id", titleWidgetId, "type", "input", "value", request.getTitle()));
        if (request.getContent() != null) {
            formItems.add(
                    Map.of("id", contentWidgetId, "type", "textarea", "value",
                            request.getContent()));
        }

        // 申请人 email 优先，否则回退到 extra（兼容旧调用方）
        String initiatorEmail = request.getApplicantEmail();
        if (initiatorEmail == null || initiatorEmail.isBlank()) {
            Map<String, String> extra = request.getExtra();
            initiatorEmail = extra != null ? extra.get(INITIATOR_EMAIL) : null;
        }
        String openId = resolveOpenId(initiatorEmail);

        // 申请人控件填充（如果配置了）
        if (applicantWidgetId != null && !applicantWidgetId.isBlank() && openId != null) {
            formItems.add(Map.of("id", applicantWidgetId, "type", "contact",
                    "value", List.of(openId)));
        }

        // 各阶段候选人 emails → 一次 batch_get 解全部 → 按阶段分发填到对应 widget
        Map<String, List<String>> stageCandidates = request.getStageCandidates();
        if (stageCandidates != null && !stageCandidates.isEmpty() && !stageFieldMapping.isEmpty()) {
            Set<String> allEmails = new HashSet<>();
            for (List<String> v : stageCandidates.values()) {
                if (v != null) {
                    allEmails.addAll(v);
                }
            }
            Map<String, String> emailToOpenId = batchResolveOpenIdsAsMap(new ArrayList<>(allEmails));
            for (Map.Entry<String, List<String>> entry : stageCandidates.entrySet()) {
                String stage = entry.getKey();
                String widgetId = stageFieldMapping.get(stage);
                if (widgetId == null || widgetId.isBlank()) {
                    log.warn("Lark approval stage '{}' has no widget mapping, skipping", stage);
                    continue;
                }
                List<String> openIds = entry.getValue() == null ? List.of()
                        : entry.getValue().stream()
                                .map(emailToOpenId::get)
                                .filter(Objects::nonNull)
                                .toList();
                if (openIds.isEmpty()) {
                    log.warn("Lark approval stage '{}' resolved 0 open_ids from {} emails — "
                            + "审批可能因审批人为空被飞书拒绝", stage, entry.getValue().size());
                }
                formItems.add(Map.of("id", widgetId, "type", "contact", "value", openIds));
            }
        }

        try {
            CreateInstanceReq req = CreateInstanceReq.newBuilder()
                    .instanceCreate(InstanceCreate.newBuilder()
                            .approvalCode(approvalCode)
                            .form(JsonUtils.toJson(formItems))
                            .openId(openId)
                            .build())
                    .build();

            CreateInstanceResp resp = client.approval().v4().instance().create(req);
            if (!resp.success()) {
                throw new RuntimeException(
                        "Failed to create Lark approval instance: " + resp.getMsg());
            }

            String instanceCode = resp.getData().getInstanceCode();
            log.info("Created Lark approval instance: {}", instanceCode);
            return instanceCode;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Lark approval instance", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApprovalCallbackResult handleCallback(String rawBody, Map<String, String> headers) {
        String body = rawBody;

        if (encryptKey != null && !encryptKey.isEmpty()) {
            String signature = headers.get("x-lark-signature");
            String timestamp = headers.get("x-lark-request-timestamp");
            String nonce = headers.get("x-lark-request-nonce");
            if (signature != null && timestamp != null && nonce != null) {
                String expected = calculateSignature(timestamp, nonce, encryptKey, rawBody);
                if (!signature.equals(expected)) {
                    log.warn("Lark callback signature mismatch");
                    return ApprovalCallbackResult.empty();
                }
            }
        }

        if (encryptKey != null && !encryptKey.isEmpty()) {
            try {
                Map<String, Object> envelope = JsonUtils.fromJson(rawBody, Map.class);
                String encrypt = (String) envelope.get("encrypt");
                if (encrypt != null) {
                    body = decrypt(encrypt, encryptKey);
                }
            } catch (Exception e) {
                log.error("Failed to decrypt Lark callback", e);
                return ApprovalCallbackResult.empty();
            }
        }

        Map<String, Object> event = JsonUtils.fromJson(body, Map.class);

        String type = (String) event.get("type");
        if ("url_verification".equals(type)) {
            String challenge = (String) event.get("challenge");
            log.info("Lark URL verification, returning challenge");
            return ApprovalCallbackResult.ofDirectResponse(Map.of("challenge", challenge));
        }

        if (verificationToken != null && !verificationToken.isEmpty()) {
            String eventToken = (String) event.get("token");
            if (!verificationToken.equals(eventToken)) {
                log.warn("Lark callback token mismatch");
                return ApprovalCallbackResult.empty();
            }
        }

        // v2.0 事件格式
        Map<String, Object> header = (Map<String, Object>) event.get("header");
        if (header != null) {
            return handleV2Event(event, header);
        }

        // v1.0 事件格式
        if ("event_callback".equals(type)) {
            Map<String, Object> eventBody = (Map<String, Object>) event.get("event");
            if (eventBody != null) {
                String eventType = (String) eventBody.get("type");
                if ("approval_instance".equals(eventType)) {
                    return handleV1InstanceEvent(eventBody);
                }
                log.debug("Ignoring Lark v1 event type: {}", eventType);
            }
            return ApprovalCallbackResult.empty();
        }

        log.debug("Ignoring Lark event type: {}", type);
        return ApprovalCallbackResult.empty();
    }

    @SuppressWarnings("unchecked")
    private ApprovalCallbackResult handleV2Event(Map<String, Object> event,
                                                 Map<String, Object> header) {
        String eventType = (String) header.get("event_type");
        if (!"approval_instance.status_changed".equals(eventType)
                && !"approval.instance.status_change".equals(eventType)) {
            log.debug("Ignoring Lark v2 event: {}", eventType);
            return ApprovalCallbackResult.empty();
        }

        Map<String, Object> eventBody = (Map<String, Object>) event.get("event");
        if (eventBody == null) {
            return ApprovalCallbackResult.empty();
        }

        String instanceCode = (String) eventBody.get("instance_code");
        String status = (String) eventBody.get("status");

        return buildCallback(instanceCode, status);
    }

    private ApprovalCallbackResult handleV1InstanceEvent(Map<String, Object> eventBody) {
        String instanceCode = (String) eventBody.get("instance_code");
        String status = (String) eventBody.get("status");

        return buildCallback(instanceCode, status);
    }

    private ApprovalCallbackResult buildCallback(String instanceCode, String status) {
        if (instanceCode == null || status == null) {
            return ApprovalCallbackResult.empty();
        }

        ApprovalAction action;
        if ("APPROVED".equals(status)) {
            action = ApprovalAction.APPROVED;
        } else if ("REJECTED".equals(status)) {
            action = ApprovalAction.REJECTED;
        } else {
            log.debug("Ignoring Lark approval status: {}", status);
            return ApprovalCallbackResult.empty();
        }

        ApprovalCallback callback = new ApprovalCallback();
        callback.setChannel("LARK");
        callback.setExternalApprovalId(instanceCode);
        callback.setAction(action);

        log.info("Lark approval callback: instance={}, status={}", instanceCode, status);
        return ApprovalCallbackResult.ofCallback(callback);
    }

    private void ensureSubscribed() {
        if (subscribed) {
            return;
        }
        try {
            SubscribeApprovalReq req = SubscribeApprovalReq.newBuilder()
                    .approvalCode(approvalCode)
                    .build();

            SubscribeApprovalResp resp =
                    client.approval().v4().approval().subscribe(req);

            if (resp.success() || resp.getCode() == 1390007) {
                subscribed = true;
                log.info("Lark approval event subscription active for: {}", approvalCode);
            } else {
                log.warn("Failed to subscribe Lark approval events: {}", resp.getMsg());
            }
        } catch (Exception e) {
            log.warn("Failed to subscribe Lark approval events", e);
        }
    }

    /**
     * 批量解析候选人邮箱列表为 {email -> open_id} 映射。
     * 一次飞书 batch_get_id_user API 拿全部，避免多阶段时 N 次单独调用。
     * 解析不到的 email 不出现在结果 map 里。已是 open_id（ou_ 前缀）的也写进 map（key=value）方便统一查找。
     */
    Map<String, String> batchResolveOpenIdsAsMap(List<String> emailsOrOpenIds) {
        if (emailsOrOpenIds == null || emailsOrOpenIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new java.util.HashMap<>(emailsOrOpenIds.size() * 2);
        List<String> emailsToResolve = new ArrayList<>();
        for (String e : emailsOrOpenIds) {
            if (e == null || e.isBlank()) {
                continue;
            }
            String t = e.trim();
            if (t.startsWith("ou_")) {
                result.put(t, t);
            } else if (t.contains("@")) {
                emailsToResolve.add(t);
            } else {
                log.warn("Lark candidate '{}' is neither email nor open_id, skipping", t);
            }
        }
        if (emailsToResolve.isEmpty()) {
            return result;
        }
        try {
            BatchGetIdUserReq req = BatchGetIdUserReq.newBuilder()
                    .userIdType("open_id")
                    .batchGetIdUserReqBody(BatchGetIdUserReqBody.newBuilder()
                            .includeResigned(false)
                            .emails(emailsToResolve.toArray(new String[0]))
                            .build())
                    .build();
            BatchGetIdUserResp resp = client.contact().v3().user().batchGetId(req);
            if (!resp.success()) {
                log.warn("Lark batch_get_id_user failed: {}", resp.getMsg());
                return result;
            }
            UserContactInfo[] userList = resp.getData().getUserList();
            if (userList != null) {
                for (UserContactInfo u : userList) {
                    if (u.getUserId() != null && u.getEmail() != null) {
                        result.put(u.getEmail(), u.getUserId());
                    }
                }
            }
            log.info("Lark batch resolved {} emails -> {} open_ids", emailsToResolve.size(),
                    result.size() - (emailsOrOpenIds.size() - emailsToResolve.size()));
            return result;
        } catch (Exception e) {
            log.error("Lark batch_get_id_user exception", e);
            return result;
        }
    }

    /**
     * 通过飞书通讯录 SDK 将邮箱/手机号解析为 open_id。
     * 如果输入已经是 open_id（以 ou_ 开头），直接返回。
     */
    String resolveOpenId(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        identity = identity.trim();

        if (identity.startsWith("ou_")) {
            return identity;
        }

        try {
            BatchGetIdUserReqBody.Builder bodyBuilder =
                    BatchGetIdUserReqBody.newBuilder().includeResigned(false);
            if (identity.contains("@")) {
                bodyBuilder.emails(new String[]{identity});
            } else {
                bodyBuilder.mobiles(new String[]{identity});
            }

            BatchGetIdUserReq req = BatchGetIdUserReq.newBuilder()
                    .userIdType("open_id")
                    .batchGetIdUserReqBody(bodyBuilder.build())
                    .build();

            BatchGetIdUserResp resp = client.contact().v3().user().batchGetId(req);
            if (!resp.success()) {
                log.warn("Failed to resolve Lark user '{}': {}", identity, resp.getMsg());
                return null;
            }

            UserContactInfo[] userList = resp.getData().getUserList();
            if (userList != null && userList.length > 0) {
                String openId = userList[0].getUserId();
                log.info("Resolved Lark identity '{}' -> open_id: {}", identity, openId);
                return openId;
            }
            log.warn("Lark user not found for identity: {}", identity);
            return null;
        } catch (Exception e) {
            log.error("Failed to resolve Lark user identity: {}", identity, e);
            return null;
        }
    }

    /**
     * 飞书签名校验：SHA256(timestamp + nonce + encryptKey + body)
     */
    private static String calculateSignature(String timestamp, String nonce,
                                             String encryptKey, String body) {
        return CryptoUtils.sha256Hex(timestamp + nonce + encryptKey + body);
    }

    /**
     * 飞书事件回调 AES-256-CBC 解密。
     */
    private static String decrypt(String encrypt, String key) throws Exception {
        byte[] keyBytes = CryptoUtils.sha256(key);
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypt);
        byte[] iv = new byte[16];
        System.arraycopy(encryptedBytes, 0, iv, 0, 16);
        byte[] ciphertext = new byte[encryptedBytes.length - 16];
        System.arraycopy(encryptedBytes, 16, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(ciphertext);

        int padLen = decrypted[decrypted.length - 1] & 0xFF;
        return new String(decrypted, 0, decrypted.length - padLen, StandardCharsets.UTF_8);
    }
}
