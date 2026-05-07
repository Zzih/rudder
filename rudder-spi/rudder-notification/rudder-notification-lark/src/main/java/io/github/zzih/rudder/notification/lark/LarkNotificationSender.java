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
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.AT_USERS;
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.DETAIL_URL;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 飞书/Lark 自定义机器人 Webhook 通知发送器。
 * <p>
 * 使用飞书互动卡片（Interactive Card）格式发送消息，
 * 支持标题、正文、详情链接和 @指定用户。
 */
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
        Map<String, Object> body = buildCardMessage(message);
        String json = JsonUtils.toJson(body);
        log.debug("Lark notification request: {}", json);
        String response = HttpUtils.postJson(webhookUrl, json);
        // Lark webhook 即便业务失败也常返回 HTTP 200,真错在 body 的 StatusCode 非 0(或 code 非 0):
        // {"StatusCode":9499,"StatusMessage":"webhook url is invalid"} / {"code":19021,"msg":"sign match fail"}
        Map<String, Object> resp = parseRespQuiet(response);
        Object code = resp.getOrDefault("StatusCode", resp.get("code"));
        if (code != null && !"0".equals(code.toString())) {
            log.error("Lark webhook rejected: title={}, response={}", message.getTitle(), response);
            return;
        }
        log.info("Lark notification sent: title={}", message.getTitle());
    }

    private static Map<String, Object> parseRespQuiet(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJson(response, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Lark response not JSON: {}", response);
            return Map.of();
        }
    }

    private Map<String, Object> buildCardMessage(NotificationMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");

        Map<String, String> extra = message.getExtra();
        Map<String, Object> card = new LinkedHashMap<>();

        // header
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", Map.of("content", defaultStr(message.getTitle()), "tag", "plain_text"));
        header.put("template", levelToTemplate(message.getLevel()));
        card.put("header", header);

        // elements
        List<Map<String, Object>> elements = new ArrayList<>();

        if (message.getContent() != null && !message.getContent().isEmpty()) {
            elements.add(Map.of("tag", "div",
                    "text", Map.of("content", message.getContent(), "tag", "lark_md")));
        }

        String atUsers = extra.get(AT_USERS);
        if (atUsers != null && !atUsers.isEmpty()) {
            StringBuilder mentions = new StringBuilder();
            for (String user : atUsers.split(",")) {
                mentions.append("<at id=\"").append(user.trim()).append("\"></at> ");
            }
            elements.add(Map.of("tag", "div",
                    "text", Map.of("content", mentions.toString().trim(), "tag", "lark_md")));
        }

        String detailUrl = extra.get(DETAIL_URL);
        if (detailUrl != null && !detailUrl.isEmpty()) {
            elements.add(Map.of("tag", "action",
                    "actions", List.of(Map.of(
                            "tag", "button",
                            "text", Map.of("content", "View Details", "tag", "plain_text"),
                            "url", detailUrl,
                            "type", "primary"))));
        }

        card.put("elements", elements);
        body.put("card", card);
        return body;
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
}
