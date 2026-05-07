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

package io.github.zzih.rudder.notification.slack;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;
import static io.github.zzih.rudder.notification.api.NotificationUtils.defaultStr;
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.AT_USERS;
import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.DETAIL_URL;

import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.slack.api.Slack;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Slack 官方 SDK 的 Incoming Webhook 通知发送器。
 */
@Slf4j
public class SlackNotificationSender implements NotificationSender {

    private static final Slack SLACK = Slack.getInstance();
    private final String webhookUrl;

    public SlackNotificationSender(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String getProvider() {
        return "SLACK";
    }

    @Override
    public void send(NotificationMessage message) {
        Payload payload = Payload.builder()
                .text(defaultStr(message.getTitle()))
                .blocks(buildBlocks(message))
                .build();

        try {
            WebhookResponse response = SLACK.send(webhookUrl, payload);
            if (response.getCode() != 200) {
                log.warn("Slack webhook returned {}: {}", response.getCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("Failed to send Slack notification", e);
        }
    }

    private List<LayoutBlock> buildBlocks(NotificationMessage message) {
        Map<String, String> extra = message.getExtra();
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(
                plainText(levelEmoji(message.getLevel()) + " " + defaultStr(message.getTitle())))));

        if (message.getContent() != null && !message.getContent().isEmpty()) {
            String text = message.getContent();
            String atUsers = extra.get(AT_USERS);
            if (atUsers != null && !atUsers.isEmpty()) {
                StringBuilder mentions = new StringBuilder("\n");
                for (String user : atUsers.split(",")) {
                    mentions.append("<@").append(user.trim()).append("> ");
                }
                text += mentions.toString().stripTrailing();
            }
            String finalText = text;
            blocks.add(section(s -> s.text(markdownText(finalText))));
        }

        String detailUrl = extra.get(DETAIL_URL);
        if (detailUrl != null && !detailUrl.isEmpty()) {
            blocks.add(actions(a -> a.elements(asElements(
                    button(b -> b.text(plainText("View Details")).url(detailUrl))))));
        }

        return blocks;
    }

    private String levelEmoji(NotificationLevel level) {
        if (level == null) {
            return ":information_source:";
        }
        return switch (level) {
            case SUCCESS -> ":white_check_mark:";
            case WARN -> ":warning:";
            case FAIL, ERROR -> ":x:";
        };
    }
}
