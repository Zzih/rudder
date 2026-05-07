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

import io.github.zzih.rudder.notification.api.NotificationSender;
import io.github.zzih.rudder.notification.api.spi.NotificationSenderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

@AutoService(NotificationSenderFactory.class)
public class LarkNotificationSenderFactory implements NotificationSenderFactory {

    @Override
    public String getProvider() {
        return "LARK";
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("webhookUrl").label("Webhook URL").type("input")
                        .required(true).placeholder("https://open.feishu.cn/open-apis/bot/v2/hook/...")
                        .build());
    }

    @Override
    public NotificationSender create(ProviderContext ctx, Map<String, String> config) {
        return new LarkNotificationSender(
                config.getOrDefault("webhookUrl", ""));
    }
}
