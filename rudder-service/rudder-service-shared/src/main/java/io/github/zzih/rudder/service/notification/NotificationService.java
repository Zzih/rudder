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

package io.github.zzih.rudder.service.notification;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.NotificationConfigDao;
import io.github.zzih.rudder.dao.entity.NotificationConfig;
import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.model.NotificationEventType;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.notification.api.plugin.NotificationPluginManager;

import java.util.Arrays;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationPluginManager pluginManager;
    private final NotificationConfigDao notificationConfigDao;

    /**
     * 发送通知。
     * <p>
     * 配置解析规则：
     * <ul>
     *   <li>NODE_OFFLINE → 直接使用平台级配置</li>
     *   <li>APPROVAL → 优先使用 workspace 级配置，无配置时 fallback 到平台级配置</li>
     * </ul>
     */
    @Async
    public void notify(NotificationMessage message, NotificationEventType eventType, Long workspaceId) {
        NotificationConfig config = resolveConfig(eventType, workspaceId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.debug("通知未启用, eventType={}, workspaceId={}", eventType, workspaceId);
            return;
        }

        if (!isEventSubscribed(config, eventType)) {
            log.debug("事件未订阅, eventType={}, workspaceId={}", eventType, workspaceId);
            return;
        }

        doSend(message, config);
    }

    /**
     * 使用指定配置直接发送通知（跳过启用检查和事件订阅过滤），用于测试。
     */
    public void sendDirect(NotificationMessage message, NotificationConfig config) {
        doSend(message, config);
    }

    /**
     * 按 id 拉配置后直接发送(测试通知用)。Controller 用这个,避免持 entity。
     */
    public void sendDirectById(Long configId, NotificationMessage message) {
        NotificationConfig config = notificationConfigDao.selectById(configId);
        if (config == null) {
            return;
        }
        doSend(message, config);
    }

    private void doSend(NotificationMessage message, NotificationConfig config) {
        try {
            String channel = config.getChannel();
            Map<String, String> channelConfig = JsonUtils.toMap(config.getChannelParams());
            log.info("发送通知, channel={}, title={}", channel, message.getTitle());
            NotificationSender sender = pluginManager.create(channel, channelConfig);
            sender.send(message);
        } catch (Exception e) {
            log.error("Failed to send notification: {}", message.getTitle(), e);
        }
    }

    /**
     * 解析生效的配置：workspace 级 > 平台级 DB。
     */
    private NotificationConfig resolveConfig(NotificationEventType eventType, Long workspaceId) {
        // 平台级事件只用平台级配置
        if (!eventType.isPlatformOnly() && workspaceId != null) {
            NotificationConfig wsConfig = notificationConfigDao.selectByWorkspaceId(workspaceId);
            if (wsConfig != null && Boolean.TRUE.equals(wsConfig.getEnabled())) {
                return wsConfig;
            }
        }

        return notificationConfigDao.selectPlatformConfig();
    }

    private boolean isEventSubscribed(NotificationConfig config, NotificationEventType eventType) {
        String subscribed = config.getSubscribedEvents();
        if (subscribed == null || subscribed.isBlank()) {
            return false;
        }
        return Arrays.stream(subscribed.split(","))
                .map(String::trim)
                .anyMatch(e -> e.equalsIgnoreCase(eventType.name()));
    }
}
