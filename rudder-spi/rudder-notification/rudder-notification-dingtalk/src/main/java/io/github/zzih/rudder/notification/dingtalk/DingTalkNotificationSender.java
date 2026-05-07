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
import static io.github.zzih.rudder.notification.api.NotificationUtils.levelEmoji;
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.AT_USERS;
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.DETAIL_URL;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉自定义机器人 Webhook 通知发送器。
 * <p>
 * 使用 ActionCard 消息类型发送，支持加签（HMAC-SHA256）安全校验。
 */
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
        String url = buildSignedUrl();
        Map<String, Object> body = buildActionCard(message);
        String json = JsonUtils.toJson(body);
        log.debug("DingTalk notification request: {}", json);
        HttpUtils.postJson(url, json);
    }

    private Map<String, Object> buildActionCard(NotificationMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "actionCard");

        Map<String, String> extra = message.getExtra();

        StringBuilder text = new StringBuilder();
        text.append("### ").append(levelEmoji(message.getLevel())).append(" ").append(defaultStr(message.getTitle()))
                .append("\n\n");

        if (message.getContent() != null && !message.getContent().isEmpty()) {
            text.append("> ").append(message.getContent()).append("\n\n");
        }

        // @ users
        String atUsers = extra.get(AT_USERS);
        if (atUsers != null && !atUsers.isEmpty()) {
            for (String user : atUsers.split(",")) {
                text.append("@").append(user.trim()).append(" ");
            }
            text.append("\n\n");
        }

        Map<String, Object> actionCard = new LinkedHashMap<>();
        actionCard.put("title", defaultStr(message.getTitle()));
        actionCard.put("text", text.toString());

        String detailUrl = extra.get(DETAIL_URL);
        if (detailUrl != null && !detailUrl.isEmpty()) {
            actionCard.put("singleTitle", "View Details");
            actionCard.put("singleURL", detailUrl);
        }

        body.put("actionCard", actionCard);
        return body;
    }

    /**
     * 如果配置了加签密钥，在 webhook URL 上附加 timestamp + sign 参数。
     */
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
