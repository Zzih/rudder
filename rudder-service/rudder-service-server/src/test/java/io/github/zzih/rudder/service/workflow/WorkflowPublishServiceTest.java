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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.PublishRecordDao;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.PublishRecord;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.enums.PublishStatus;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;
import io.github.zzih.rudder.service.config.PublishConfigService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowPublishServiceTest {

    @Mock
    private PublishRecordDao publishRecordDao;
    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;
    @Mock
    private ProjectDao projectDao;
    @Mock
    private TaskDefinitionDao taskDefinitionDao;
    @Mock
    private ScriptDao scriptDao;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private WorkflowVersionService workflowVersionService;
    @Mock
    private WorkflowScheduleService workflowScheduleService;
    @Mock
    private VersionService versionService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserDao userDao;
    @Mock
    private PublishConfigService publishConfigService;
    @Mock
    private Publisher publisher;

    @InjectMocks
    private WorkflowPublishService service;

    @Test
    @DisplayName("submitPublish: SUPER_ADMIN → 跳审批，直接通过 Publisher SPI 发布")
    void submitPublishAsSuperAdminBypassesApproval() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setCode(11L);
        workflow.setName("daily_etl");
        workflow.setWorkspaceId(5L);
        workflow.setDagJson("{}");
        when(workflowDefinitionDao.selectByCode(11L)).thenReturn(workflow);

        VersionRecord versionRecord = new VersionRecord();
        versionRecord.setVersionNo(3);
        when(workflowVersionService.saveWorkflowVersion(any(), any(), anyString())).thenReturn(versionRecord);
        when(taskDefinitionDao.selectByWorkflowDefinitionCode(11L)).thenReturn(List.of());
        when(publishConfigService.required()).thenReturn(publisher);

        var admin = new UserContext.UserInfo(100L, "admin", 5L, null, RoleType.SUPER_ADMIN.name());
        var result = UserContext.runWith(admin, () -> service.submitPublish(11L, null, 7L, "release"));

        assertThat(result).isNotNull();
        // 通过 Publisher SPI 发布
        verify(publisher).publishWorkflow(any(WorkflowPublishBundle.class));
        // 不再走审批
        verify(approvalService, never()).submit(any(), anyString(), anyLong(), anyLong(), any(), anyString());
        // 状态推到 PUBLISHED
        verify(publishRecordDao).updateById(any());
    }

    @Test
    @DisplayName("submitPublish: 普通用户 → 走审批流，不直接发布")
    void submitPublishAsNonAdminTriggersApproval() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setCode(11L);
        workflow.setName("daily_etl");
        workflow.setWorkspaceId(5L);
        workflow.setDagJson("{}");
        when(workflowDefinitionDao.selectByCode(11L)).thenReturn(workflow);

        VersionRecord versionRecord = new VersionRecord();
        versionRecord.setVersionNo(3);
        when(workflowVersionService.saveWorkflowVersion(any(), any(), anyString())).thenReturn(versionRecord);

        var dev = new UserContext.UserInfo(100L, "alice", 5L, null, RoleType.DEVELOPER.name());
        var result = UserContext.runWith(dev, () -> service.submitPublish(11L, null, 7L, "release"));

        assertThat(result).isNotNull();
        // 提交审批，不直接发布
        verify(approvalService).submit(any(), anyString(), any(), eq(5L), any(), anyString());
        verify(publisher, never()).publishWorkflow(any());
        // 状态保持 PENDING_APPROVAL（无 updateById 调用）
        verify(publishRecordDao, never()).updateById(any());
        // PublishRecord 入库时 status=PENDING_APPROVAL
        verify(publishRecordDao).insert(
                argThat(r -> r instanceof PublishRecord pr && pr.getStatus() == PublishStatus.PENDING_APPROVAL));
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> m) {
        return org.mockito.ArgumentMatchers.argThat(m);
    }
}
