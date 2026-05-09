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

package io.github.zzih.rudder.approval.kissflow;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.spi.ApprovalNotifierFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(ApprovalNotifierFactory.class)
public class KissflowApprovalNotifierFactory implements ApprovalNotifierFactory<KissflowApprovalProperties> {

    @Override
    public String getProvider() {
        return "KISSFLOW";
    }

    @Override
    public Class<KissflowApprovalProperties> propertiesClass() {
        return KissflowApprovalProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("apiKey").label("API Key").type("password")
                        .required(true).placeholder("Kissflow API Key")
                        .build(),
                PluginParamDefinition.builder()
                        .name("accountId").label("Account ID").type("input")
                        .required(true).placeholder("Kissflow Account ID")
                        .build(),
                PluginParamDefinition.builder()
                        .name("processId").label("Process ID").type("input")
                        .required(true).placeholder("spi.approval.kissflow.processId.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("titleField").label("spi.approval.kissflow.titleField.label").type("input")
                        .required(false).placeholder("spi.approval.kissflow.titleField.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("contentField").label("spi.approval.kissflow.contentField.label").type("input")
                        .required(false).placeholder("spi.approval.kissflow.contentField.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("applicantField").label("spi.approval.kissflow.applicantField.label").type("input")
                        .required(false).placeholder("spi.approval.kissflow.applicantField.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("stageFieldMapping").label("spi.approval.kissflow.stageFieldMapping.label")
                        .type("textarea")
                        .required(false)
                        .placeholder(
                                "{\"PROJECT_OWNER\":\"Approver_Level_1\",\"WORKSPACE_OWNER\":\"Approver_Level_2\"}")
                        .build());
    }

    @Override
    public ApprovalNotifier create(ProviderContext ctx, KissflowApprovalProperties props) {
        return new KissflowApprovalNotifier(
                props.apiKey(), props.accountId(), props.processId(),
                props.titleField(), props.contentField(), props.applicantField(),
                props.stageFieldMapping());
    }
}
