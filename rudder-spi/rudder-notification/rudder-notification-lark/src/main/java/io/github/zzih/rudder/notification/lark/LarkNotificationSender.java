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

package io.github.zzih.rudder.notification.lark;

import static io.github.zzih.rudder.notification.api.NotificationUtils.defaultStr;
import static io.github.zzih.rudder.notification.api.NotificationUtils.formatNodeList;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 飞书自定义机器人 Webhook 发送器。每个 message 子类型对应一个独立 build 方法，产出完整 interactive card body。 */
@Slf4j
@RequiredArgsConstructor
public class LarkNotificationSender implements NotificationSender {

    private final String webhookUrl;

    @Override
    public String getProvider() {
        return "LARK";
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
        log.debug("Lark notification request: {}", json);
        String response = HttpUtils.postJson(webhookUrl, json);
        // Lark webhook 即便业务失败也常返回 HTTP 200，真正错误在 body 的 StatusCode/code 字段
        Map<String, Object> resp = parseRespQuiet(response);
        Object code = resp.getOrDefault("StatusCode", resp.get("code"));
        if (code != null && !"0".equals(code.toString())) {
            log.error("Lark webhook rejected: response={}", response);
            return;
        }
        log.info("Lark notification sent");
    }

    // ==================== 每个 message 类型对应一个 build 方法 ====================

    private Map<String, Object> buildApprovalSubmitted(ApprovalSubmittedMessage m) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            elements.add(divLarkMd(m.resourceContent()));
        }
        elements.add(fieldsBlock(List.of(
                field("Submitter", userDisplay(m.submitter())),
                field("Approvers", userListDisplay(m.approvers())))));
        if (m.remark() != null && !m.remark().isBlank()) {
            elements.add(divLarkMd("**Remark**: " + m.remark()));
        }
        if (!m.approvers().isEmpty()) {
            elements.add(mentionsBlock(m.approvers()));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            elements.add(actionButton("Go Approve", m.detailUrl(), "primary"));
        }
        return wrapCard(m.resourceTitle(), m.level(), elements);
    }

    private Map<String, Object> buildApprovalApproved(ApprovalApprovedMessage m) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            elements.add(divLarkMd(m.resourceContent()));
        }
        elements.add(fieldsBlock(List.of(
                field("Approved by", userDisplay(m.approver())),
                field("Submitter", userDisplay(m.submitter())))));
        if (m.comment() != null && !m.comment().isBlank()) {
            elements.add(divLarkMd("**Comment**: " + m.comment()));
        }
        if (m.submitter() != null) {
            elements.add(mentionsBlock(List.of(m.submitter())));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            elements.add(actionButton("View Details", m.detailUrl(), "default"));
        }
        return wrapCard(m.resourceTitle(), m.level(), elements);
    }

    private Map<String, Object> buildApprovalRejected(ApprovalRejectedMessage m) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            elements.add(divLarkMd(m.resourceContent()));
        }
        elements.add(fieldsBlock(List.of(
                field("Rejected by", userDisplay(m.approver())),
                field("Submitter", userDisplay(m.submitter())))));
        if (m.reason() != null && !m.reason().isBlank()) {
            elements.add(divLarkMd("**Reason**: " + m.reason()));
        }
        if (m.submitter() != null) {
            elements.add(mentionsBlock(List.of(m.submitter())));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            elements.add(actionButton("View Details", m.detailUrl(), "default"));
        }
        return wrapCard(m.resourceTitle(), m.level(), elements);
    }

    private Map<String, Object> buildNodeOnline(NodeOnlineMessage m) {
        String title = m.nodes().size() == 1 ? "Rudder Node Online" : "Rudder Node(s) Online";
        return wrapCard(title, m.level(), List.of(nodesBlock(m.nodes(), "Online")));
    }

    private Map<String, Object> buildNodeOffline(NodeOfflineMessage m) {
        String title = m.graceful() ? "Rudder Node Offline" : "Rudder Node Offline Alert";
        String state = m.graceful() ? "Graceful Shutdown" : "Heartbeat Timeout";
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(nodesBlock(m.nodes(), state));
        if (!m.graceful() && !m.oncall().isEmpty()) {
            elements.add(mentionsBlock(m.oncall()));
        }
        return wrapCard(title, m.level(), elements);
    }

    private Map<String, Object> buildPlain(PlainMessage m) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (m.content() != null && !m.content().isBlank()) {
            elements.add(divLarkMd(m.content()));
        }
        return wrapCard(m.title(), m.level(), elements);
    }

    // ==================== Lark interactive card 通用积木 ====================

    private Map<String, Object> wrapCard(String title, NotificationLevel level,
                                         List<Map<String, Object>> elements) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", Map.of("content", defaultStr(title), "tag", "plain_text"));
        header.put("template", levelToTemplate(level));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", header);
        card.put("elements", elements);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", card);
        return body;
    }

    private Map<String, Object> divLarkMd(String content) {
        return Map.of("tag", "div", "text", Map.of("content", content, "tag", "lark_md"));
    }

    private Map<String, Object> fieldsBlock(List<Map<String, Object>> fields) {
        return Map.of("tag", "div", "fields", fields);
    }

    private Map<String, Object> field(String label, String value) {
        return Map.of(
                "is_short", true,
                "text", Map.of("content", "**" + label + "**\n" + (value != null ? value : "-"),
                        "tag", "lark_md"));
    }

    private Map<String, Object> nodesBlock(List<NodeInfo> nodes, String stateLabel) {
        return divLarkMd(formatNodeList(nodes, stateLabel, "-"));
    }

    private Map<String, Object> mentionsBlock(List<UserRef> users) {
        StringBuilder mentions = new StringBuilder();
        for (UserRef u : users) {
            String at = u.email() != null && !u.email().isBlank()
                    ? "<at email=\"" + u.email() + "\"></at>"
                    : "@" + defaultStr(u.username());
            mentions.append(at).append(' ');
        }
        return divLarkMd(mentions.toString().stripTrailing());
    }

    private Map<String, Object> actionButton(String label, String url, String type) {
        return Map.of("tag", "action",
                "actions", List.of(Map.of(
                        "tag", "button",
                        "text", Map.of("content", label, "tag", "plain_text"),
                        "url", url,
                        "type", type)));
    }

    private String levelToTemplate(NotificationLevel level) {
        if (level == null) {
            return "blue";
        }
        return switch (level) {
            case SUCCESS -> "green";
            case WARN -> "orange";
            case FAIL, ERROR -> "red";
        };
    }

    private static Map<String, Object> parseRespQuiet(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJson(response, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Lark response not JSON: {}", response);
            return Map.of();
        }
    }
}
