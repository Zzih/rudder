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
import static com.slack.api.model.block.Blocks.divider;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;
import static io.github.zzih.rudder.notification.api.NotificationUtils.defaultStr;
import static io.github.zzih.rudder.notification.api.NotificationUtils.formatNodeList;
import static io.github.zzih.rudder.notification.api.NotificationUtils.userDisplay;
import static io.github.zzih.rudder.notification.api.NotificationUtils.userListDisplay;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.slack.api.Slack;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;

import lombok.extern.slf4j.Slf4j;

/** Slack incoming webhook 发送器。每个 message 子类型对应一个独立 build 方法，产出完整 Payload（含 blocks）。 */
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
        Payload payload = switch (message) {
            case ApprovalSubmittedMessage m -> buildApprovalSubmitted(m);
            case ApprovalApprovedMessage m -> buildApprovalApproved(m);
            case ApprovalRejectedMessage m -> buildApprovalRejected(m);
            case NodeOnlineMessage m -> buildNodeOnline(m);
            case NodeOfflineMessage m -> buildNodeOffline(m);
            case PlainMessage m -> buildPlain(m);
        };

        try {
            WebhookResponse response = SLACK.send(webhookUrl, payload);
            if (response.getCode() != 200) {
                log.warn("Slack webhook returned {}: {}", response.getCode(), response.getBody());
                return;
            }
            log.info("Slack notification sent");
        } catch (IOException e) {
            log.error("Failed to send Slack notification", e);
        }
    }

    // ==================== 每个 message 类型对应一个 build 方法 ====================

    private Payload buildApprovalSubmitted(ApprovalSubmittedMessage m) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), m.resourceTitle()));
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            blocks.add(section(s -> s.text(markdownText(m.resourceContent()))));
        }
        blocks.add(divider());
        blocks.add(section(s -> s.fields(List.of(
                markdownText("*Submitter*\n" + userDisplay(m.submitter())),
                markdownText("*Approvers*\n" + userListDisplay(m.approvers()))))));
        if (m.remark() != null && !m.remark().isBlank()) {
            blocks.add(section(s -> s.text(markdownText("*Remark*: " + m.remark()))));
        }
        if (!m.approvers().isEmpty()) {
            blocks.add(mentionsSection(m.approvers()));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            blocks.add(actions(a -> a.elements(asElements(
                    button(b -> b.text(plainText("Go Approve")).url(m.detailUrl()).style("primary"))))));
        }
        return wrap(m.resourceTitle(), blocks);
    }

    private Payload buildApprovalApproved(ApprovalApprovedMessage m) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), m.resourceTitle()));
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            blocks.add(section(s -> s.text(markdownText(m.resourceContent()))));
        }
        blocks.add(divider());
        blocks.add(section(s -> s.fields(List.of(
                markdownText("*Approved by*\n" + userDisplay(m.approver())),
                markdownText("*Submitter*\n" + userDisplay(m.submitter()))))));
        if (m.comment() != null && !m.comment().isBlank()) {
            blocks.add(section(s -> s.text(markdownText("*Comment*: " + m.comment()))));
        }
        if (m.submitter() != null) {
            blocks.add(mentionsSection(List.of(m.submitter())));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            blocks.add(actions(a -> a.elements(asElements(
                    button(b -> b.text(plainText("View Details")).url(m.detailUrl()))))));
        }
        return wrap(m.resourceTitle(), blocks);
    }

    private Payload buildApprovalRejected(ApprovalRejectedMessage m) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), m.resourceTitle()));
        if (m.resourceContent() != null && !m.resourceContent().isBlank()) {
            blocks.add(section(s -> s.text(markdownText(m.resourceContent()))));
        }
        blocks.add(divider());
        blocks.add(section(s -> s.fields(List.of(
                markdownText("*Rejected by*\n" + userDisplay(m.approver())),
                markdownText("*Submitter*\n" + userDisplay(m.submitter()))))));
        if (m.reason() != null && !m.reason().isBlank()) {
            blocks.add(section(s -> s.text(markdownText("*Reason*: " + m.reason()))));
        }
        if (m.submitter() != null) {
            blocks.add(mentionsSection(List.of(m.submitter())));
        }
        if (m.detailUrl() != null && !m.detailUrl().isBlank()) {
            blocks.add(actions(a -> a.elements(asElements(
                    button(b -> b.text(plainText("View Details")).url(m.detailUrl()))))));
        }
        return wrap(m.resourceTitle(), blocks);
    }

    private Payload buildNodeOnline(NodeOnlineMessage m) {
        String title = m.nodes().size() == 1 ? "Rudder Node Online" : "Rudder Node(s) Online";
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), title));
        blocks.add(nodesSection(m.nodes(), "Online"));
        return wrap(title, blocks);
    }

    private Payload buildNodeOffline(NodeOfflineMessage m) {
        String title = m.graceful() ? "Rudder Node Offline" : "Rudder Node Offline Alert";
        String state = m.graceful() ? "Graceful Shutdown" : "Heartbeat Timeout";
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), title));
        blocks.add(nodesSection(m.nodes(), state));
        if (!m.graceful() && !m.oncall().isEmpty()) {
            blocks.add(mentionsSection(m.oncall()));
        }
        return wrap(title, blocks);
    }

    private Payload buildPlain(PlainMessage m) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(headerBlock(m.level(), m.title()));
        if (m.content() != null && !m.content().isBlank()) {
            blocks.add(section(s -> s.text(markdownText(m.content()))));
        }
        return wrap(m.title(), blocks);
    }

    // ==================== Slack blocks 通用积木 ====================

    private Payload wrap(String title, List<LayoutBlock> blocks) {
        return Payload.builder()
                .text(defaultStr(title))
                .blocks(blocks)
                .build();
    }

    private LayoutBlock headerBlock(NotificationLevel level, String title) {
        String text = levelEmoji(level) + " " + defaultStr(title);
        return header(h -> h.text(plainText(text)));
    }

    private LayoutBlock nodesSection(List<NodeInfo> nodes, String state) {
        return section(s -> s.text(markdownText(formatNodeList(nodes, state, "•"))));
    }

    private LayoutBlock mentionsSection(List<UserRef> users) {
        StringBuilder sb = new StringBuilder();
        for (UserRef u : users) {
            String mention;
            if (u.email() != null && !u.email().isBlank()) {
                // mailto link 不真 ping，但能引人注意——真 ping 需业务侧把 email lookup 成 Slack U123 id
                mention = "<mailto:" + u.email() + "|@" + defaultStr(u.username()) + ">";
            } else {
                mention = "@" + defaultStr(u.username());
            }
            sb.append(mention).append(' ');
        }
        String text = sb.toString().stripTrailing();
        return section(s -> s.text(markdownText(text)));
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
