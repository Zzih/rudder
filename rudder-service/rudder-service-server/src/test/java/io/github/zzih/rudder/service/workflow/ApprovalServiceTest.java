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

package io.github.zzih.rudder.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.approval.api.plugin.ApprovalPluginManager;
import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.dao.dao.ApprovalDecisionDao;
import io.github.zzih.rudder.dao.dao.ApprovalRecordDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.service.approval.integration.ApprovalIntegrationDispatcher;
import io.github.zzih.rudder.service.config.ApprovalConfigService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.workflow.approver.ApproverResolverRegistry;
import io.github.zzih.rudder.service.workflow.stage.ApprovalStageFlowRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRecordDao approvalRecordDao;
    @Mock
    private ApprovalDecisionDao approvalDecisionDao;
    @Mock
    private UserDao userDao;
    @Mock
    private ApprovalConfigService approvalConfigService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApprovalStageFlowRegistry stageFlowRegistry;
    @Mock
    private ApproverResolverRegistry approverResolverRegistry;
    @Mock
    private ApprovalIntegrationDispatcher integrationDispatcher;

    @InjectMocks
    private ApprovalService approvalService;

    @Test
    @DisplayName("submit: 审批关闭 → 自动通过且 channel 落为 LOCAL fallback(channel NOT NULL 不能漏)")
    void submitAutoApprovesWithLocalChannelWhenDisabled() {
        when(approvalConfigService.enabled()).thenReturn(false);

        ApprovalRequest req = ApprovalRequest.builder().title("t").content("c").build();
        approvalService.submit(req, "MCP_TOKEN", 7L, 2L, null, "remark");

        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordDao).insert(captor.capture());
        ApprovalRecord record = captor.getValue();
        assertThat(record.getChannel()).isEqualTo(ApprovalPluginManager.FALLBACK_PROVIDER);
        assertThat(record.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(record.getResolvedAt()).isNotNull();
    }
}
