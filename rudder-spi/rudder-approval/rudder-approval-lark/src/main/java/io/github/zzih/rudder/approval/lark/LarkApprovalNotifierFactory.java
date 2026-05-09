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

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.spi.ApprovalNotifierFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(ApprovalNotifierFactory.class)
public class LarkApprovalNotifierFactory implements ApprovalNotifierFactory<LarkApprovalProperties> {

    @Override
    public String getProvider() {
        return "LARK";
    }

    @Override
    public Class<LarkApprovalProperties> propertiesClass() {
        return LarkApprovalProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("appId").label("App ID").type("input")
                        .required(true).placeholder("cli_xxxxxxxxxxxxxxxx")
                        .build(),
                PluginParamDefinition.builder()
                        .name("appSecret").label("App Secret").type("password")
                        .required(true).placeholder("spi.approval.lark.appSecret.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("approvalCode").label("Approval Code").type("input")
                        .required(true).placeholder("spi.approval.lark.approvalCode.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("titleWidgetId").label("spi.approval.lark.titleWidgetId.label").type("input")
                        .required(true).placeholder("spi.approval.lark.titleWidgetId.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("contentWidgetId").label("spi.approval.lark.contentWidgetId.label").type("input")
                        .required(true).placeholder("spi.approval.lark.contentWidgetId.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("applicantWidgetId").label("spi.approval.lark.applicantWidgetId.label").type("input")
                        .required(false).placeholder("spi.approval.lark.applicantWidgetId.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("stageFieldMapping").label("spi.approval.lark.stageFieldMapping.label").type("textarea")
                        .required(false)
                        .placeholder("{\"PROJECT_OWNER\":\"widget_xxx\",\"WORKSPACE_OWNER\":\"widget_yyy\"}")
                        .build(),
                PluginParamDefinition.builder()
                        .name("encryptKey").label("Encrypt Key").type("password")
                        .required(false).placeholder("spi.approval.lark.encryptKey.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("verificationToken").label("Verification Token").type("password")
                        .required(false).placeholder("spi.approval.lark.verificationToken.placeholder")
                        .build());
    }

    @Override
    public ApprovalNotifier create(ProviderContext ctx, LarkApprovalProperties props) {
        return new LarkApprovalNotifier(
                props.appId(), props.appSecret(), props.approvalCode(),
                props.titleWidgetId(), props.contentWidgetId(), props.applicantWidgetId(),
                props.stageFieldMapping(), props.encryptKey(), props.verificationToken());
    }
}
