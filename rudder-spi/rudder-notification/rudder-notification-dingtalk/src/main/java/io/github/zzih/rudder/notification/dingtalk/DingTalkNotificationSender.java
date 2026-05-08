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

package io.github.zzih.rudder.notification.dingtalk;

import static io.github.zzih.rudder.notification.api.NotificationUtils.defaultStr;
import static io.github.zzih.rudder.notification.api.NotificationUtils.formatNodeList;
import static io.github.zzih.rudder.notification.api.NotificationUtils.levelEmoji;
import static io.github.zzih.rudder.notification.api.NotificationUtils.singletonOrEmpty;
import static io.github.zzih.rudder.notification.api.NotificationUtils.userDisplay;
import static io.github.zzih.rudder.notification.api.NotificationUtils.userListDisplay;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.ApprovalApprovedMessage;
import io.github.zzih.rudder.notification.api.model.ApprovalRejectedMessage;
import io.github.zzih.rudder.notification.api.model.ApprovalSubmittedMessage;
import io.github.zzih.rudder.notification.api.model.NodeInfo;
import io.github.zzih.rudder.notification.api.model.NodeOfflineMessage;
import io.github.zzih.rudder.notification.api.model.NodeOnlineMessage;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.notification.api.model.PlainMessage;
import io.github.zzih.rudder.notification.api.model.UserRef;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 钉钉自定义机器人 Webhook 发送器。每个 message 子类型对应一个独立 build 方法，产出完整 actionCard body。 */
@Slf4j
@RequiredArgsConstructor
public class DingTalkNotificationSender implements NotificationSender {

    private final String webhookUrl;
    private final String secret;

    @Override
    public String getProvider() {
        return "DINGTALK";
    }

    @Override
    public void send(NotificationMessage message) {
        Map<String, Object> body = switch (message) {
            case ApprovalSubmittedMessage m -> buildApprovalSubmitted(m);
            case ApprovalApprovedMessage m -> buildApprovalApproved(m);
            case ApprovalRejectedMessage m -> buildApprovalRejected(m);
            case NodeOnlineMessage m -> buildNodeOnline(m);
            case NodeOfflineMessage m -> buildNodeOffline(m);
            case PlainMessage m -> buildPlain(m);
        };

        String json = JsonUtils.toJson(body);
        log.debug("DingTalk notification request: {}", json);
        HttpUtils.postJson(buildSignedUrl(), json);
        log.info("DingTalk notification sent");
    }

    // ==================== 每个 message 类型对应一个 build 方法 ====================

    private Map<String, Object> buildApprovalSubmitted(ApprovalSubmittedMessage m) {
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), m.resourceTitle());
        appendQuote(md, m.resourceContent());
        md.append("**Submitter**: ").append(userDisplay(m.submitter())).append("  \n");
        md.append("**Approvers**: ").append(userListDisplay(m.approvers())).append("\n\n");
        appendBold(md, "Remark", m.remark());
        appendMentions(md, m.approvers());
        return wrapActionCard(m.resourceTitle(), md.toString(), "Go Approve", m.detailUrl(), m.approvers());
    }

    private Map<String, Object> buildApprovalApproved(ApprovalApprovedMessage m) {
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), m.resourceTitle());
        appendQuote(md, m.resourceContent());
        md.append("**Approved by**: ").append(userDisplay(m.approver())).append("  \n");
        md.append("**Submitter**: ").append(userDisplay(m.submitter())).append("\n\n");
        appendBold(md, "Comment", m.comment());
        List<UserRef> mentions = singletonOrEmpty(m.submitter());
        appendMentions(md, mentions);
        return wrapActionCard(m.resourceTitle(), md.toString(), "View Details", m.detailUrl(), mentions);
    }

    private Map<String, Object> buildApprovalRejected(ApprovalRejectedMessage m) {
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), m.resourceTitle());
        appendQuote(md, m.resourceContent());
        md.append("**Rejected by**: ").append(userDisplay(m.approver())).append("  \n");
        md.append("**Submitter**: ").append(userDisplay(m.submitter())).append("\n\n");
        appendBold(md, "Reason", m.reason());
        List<UserRef> mentions = singletonOrEmpty(m.submitter());
        appendMentions(md, mentions);
        return wrapActionCard(m.resourceTitle(), md.toString(), "View Details", m.detailUrl(), mentions);
    }

    private Map<String, Object> buildNodeOnline(NodeOnlineMessage m) {
        String title = m.nodes().size() == 1 ? "Rudder Node Online" : "Rudder Node(s) Online";
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), title);
        appendNodes(md, m.nodes(), "Online");
        return wrapActionCard(title, md.toString(), null, null, List.of());
    }

    private Map<String, Object> buildNodeOffline(NodeOfflineMessage m) {
        String title = m.graceful() ? "Rudder Node Offline" : "Rudder Node Offline Alert";
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), title);
        appendNodes(md, m.nodes(), m.graceful() ? "Graceful Shutdown" : "Heartbeat Timeout");
        List<UserRef> mentions = !m.graceful() ? m.oncall() : List.of();
        appendMentions(md, mentions);
        return wrapActionCard(title, md.toString(), null, null, mentions);
    }

    private Map<String, Object> buildPlain(PlainMessage m) {
        StringBuilder md = new StringBuilder();
        appendHeader(md, m.level(), m.title());
        if (m.content() != null && !m.content().isBlank()) {
            md.append(m.content()).append('\n');
        }
        return wrapActionCard(m.title(), md.toString(), null, null, List.of());
    }

    // ==================== DingTalk actionCard 通用积木 ====================

    private Map<String, Object> wrapActionCard(String title, String text, String singleTitle, String singleUrl,
                                               List<UserRef> mentions) {
        Map<String, Object> actionCard = new LinkedHashMap<>();
        actionCard.put("title", defaultStr(title));
        actionCard.put("text", text);
        if (singleUrl != null && !singleUrl.isBlank()) {
            actionCard.put("singleTitle", singleTitle);
            actionCard.put("singleURL", singleUrl);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "actionCard");
        body.put("actionCard", actionCard);
        if (!mentions.isEmpty()) {
            body.put("at", buildAt(mentions));
        }
        return body;
    }

    /** 钉钉真 @ 走 atUserIds（企业内）/ atMobiles（手机号）。webhook 群机器人最稳是 atMobiles。 */
    private Map<String, Object> buildAt(List<UserRef> mentions) {
        List<String> mobiles = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (UserRef u : mentions) {
            String key = u.email() != null && !u.email().isBlank() ? u.email() : u.username();
            if (key == null) {
                continue;
            }
            if (key.matches("\\d{11}")) {
                mobiles.add(key);
            } else {
                userIds.add(key);
            }
        }
        Map<String, Object> at = new LinkedHashMap<>();
        if (!mobiles.isEmpty()) {
            at.put("atMobiles", mobiles);
        }
        if (!userIds.isEmpty()) {
            at.put("atUserIds", userIds);
        }
        at.put("isAtAll", false);
        return at;
    }

    private void appendHeader(StringBuilder md, NotificationLevel level, String title) {
        md.append("### ").append(levelEmoji(level)).append(' ').append(defaultStr(title)).append("\n\n");
    }

    private void appendQuote(StringBuilder md, String value) {
        if (value != null && !value.isBlank()) {
            md.append("> ").append(value).append("\n\n");
        }
    }

    private void appendBold(StringBuilder md, String label, String value) {
        if (value != null && !value.isBlank()) {
            md.append("**").append(label).append("**: ").append(value).append("\n\n");
        }
    }

    private void appendNodes(StringBuilder md, List<NodeInfo> nodes, String state) {
        md.append(formatNodeList(nodes, state, "-")).append('\n');
    }

    private void appendMentions(StringBuilder md, List<UserRef> mentions) {
        if (mentions.isEmpty()) {
            return;
        }
        md.append('\n');
        for (UserRef u : mentions) {
            String key = u.email() != null && !u.email().isBlank() ? u.email() : defaultStr(u.username());
            md.append('@').append(key).append(' ');
        }
    }

    /** 如果配置了加签密钥，在 webhook URL 上附加 timestamp + sign 参数。 */
    private String buildSignedUrl() {
        if (secret == null || secret.isEmpty()) {
            return webhookUrl;
        }
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(java.util.Base64.getEncoder().encodeToString(signData),
                    StandardCharsets.UTF_8);
            String separator = webhookUrl.contains("?") ? "&" : "?";
            return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.warn("Failed to sign DingTalk webhook URL, sending without signature", e);
            return webhookUrl;
        }
    }
}
