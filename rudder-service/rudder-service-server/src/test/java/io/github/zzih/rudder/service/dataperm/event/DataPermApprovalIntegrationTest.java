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

package io.github.zzih.rudder.service.dataperm.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.ApprovalRecordDao;
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.dto.DataPermApplyContext;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.ResourcePathDTO;
import io.github.zzih.rudder.service.dataperm.service.DataPermApplyService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataPermApprovalIntegrationTest {

    @Mock
    private ApprovalRecordDao approvalRecordDao;
    @Mock
    private DataPermBundleDao bundleDao;
    @Mock
    private DataPermConfigService configService;
    @Mock
    private io.github.zzih.rudder.service.dataperm.service.DataPermScopeAccessGroupService accessGroupService;
    @Mock
    private DataPermUserBundleGrantDao userBundleGrantDao;
    @Mock
    private DataPermUserDirectGrantDao userDirectBlockDao;
    @Mock
    private io.github.zzih.rudder.service.dataperm.reconciler.DataPermReconciler reconciler;
    @Mock
    private io.github.zzih.rudder.service.dataperm.adapter.RangerAdapterRegistry adapterRegistry;

    @InjectMocks
    private DataPermApprovalIntegration integration;

    private static ApprovalFinalizedEvent event(String finalStatus, Long approvalId) {
        return new ApprovalFinalizedEvent(
                approvalId, ApprovalResourceType.DATA_PERM_APPLY, 100L, finalStatus, 5L, 88L);
    }

    private static ApprovalRecord recordWithContext(Long approvalId, DataPermApplyContext context) {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(approvalId);
        record.setResourceType(ApprovalResourceType.DATA_PERM_APPLY);
        String contextJson = JsonUtils.toJson(context);
        record.setExtData(JsonUtils.toJson(Map.of(DataPermApplyService.EXTRA_KEY_DATA_PERM, contextJson)));
        return record;
    }

    private static DataPermApplyContext context(Long applicant, List<Long> bundleIds,
                                                List<DataPermStatementDTO> direct,
                                                LocalDateTime expireAt) {
        return new DataPermApplyContext(applicant, 5L, bundleIds, direct, expireAt);
    }

    private static DataPermStatementDTO sampleBlock() {
        DataPermStatementDTO b = new DataPermStatementDTO();
        b.setScopeCode(7L);
        b.setGroupIds(List.of(1L));
        ResourcePathDTO r = new ResourcePathDTO();
        r.setDatabaseNames(List.of("ods"));
        r.setTableNames(List.of("orders"));
        b.setResources(List.of(r));
        return b;
    }

    @Test
    @DisplayName("resourceType 返回 DATA_PERM_APPLY")
    void resourceTypeIsDataPermApply() {
        assertThat(integration.resourceType()).isEqualTo(ApprovalResourceType.DATA_PERM_APPLY);
    }

    @Test
    @DisplayName("非 APPROVED 状态 → 业务侧无操作 / 不查 DAO")
    void rejectedNoop() {
        integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_REJECTED, 42L));
        verify(userBundleGrantDao, never()).insert(any());
        verify(userDirectBlockDao, never()).insertStatement(any());
    }

    @Test
    @DisplayName("APPROVED + 幂等: 已存在 role grants → skip")
    void idempotentOnExistingRoleGrants() {
        when(userBundleGrantDao.selectByApprovalId(42L)).thenReturn(List.of(new DataPermUserBundleGrant()));
        integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_APPROVED, 42L));
        verify(approvalRecordDao, never()).selectById(anyLong());
        verify(userBundleGrantDao, never()).insert(any());
    }

    @Test
    @DisplayName("APPROVED + 完整 context → 创建 role grants + direct 块")
    void approvedCreatesGrants() {
        var ctx = context(99L, List.of(1L, 2L), List.of(sampleBlock()), null);
        when(userBundleGrantDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(userDirectBlockDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(approvalRecordDao.selectById(42L)).thenReturn(recordWithContext(42L, ctx));
        when(bundleDao.selectById(1L)).thenReturn(new DataPermBundle());
        when(bundleDao.selectById(2L)).thenReturn(new DataPermBundle());
        DataPermScopeDTO scope = new DataPermScopeDTO();
        scope.setCode(7L);
        scope.setName("hive_prod");
        scope.setPluginType(PluginType.HADOOP_SQL);
        scope.setMetadataDatasourceId(1L);
        scope.setManagedTaskTypes(java.util.List.of());
        scope.setRangerServiceName("hive_prod");
        scope.setEnabled(true);
        when(configService.findScope(7L)).thenReturn(java.util.Optional.of(scope));

        integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_APPROVED, 42L));

        verify(userBundleGrantDao, times(2)).insert(any(DataPermUserBundleGrant.class));
        verify(userDirectBlockDao, times(1)).insertStatement(any(DataPermUserDirectGrant.class));
    }

    @Test
    @DisplayName("APPROVED + role 在审批期间被删 → 抛 RESOURCE_MISSING")
    void missingRoleThrows() {
        var ctx = context(99L, List.of(1L), List.of(), null);
        when(userBundleGrantDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(userDirectBlockDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(approvalRecordDao.selectById(42L)).thenReturn(recordWithContext(42L, ctx));
        when(bundleDao.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_APPROVED, 42L)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.RESOURCE_MISSING);
        verify(userBundleGrantDao, never()).insert(any());
    }

    @Test
    @DisplayName("APPROVED + scope 在审批期间被删 → 抛 RESOURCE_MISSING")
    void missingScopeThrows() {
        var ctx = context(99L, List.of(), List.of(sampleBlock()), null);
        when(userBundleGrantDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(userDirectBlockDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(approvalRecordDao.selectById(42L)).thenReturn(recordWithContext(42L, ctx));
        when(configService.findScope(7L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_APPROVED, 42L)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.RESOURCE_MISSING);
        verify(userDirectBlockDao, never()).insertStatement(any());
    }

    @Test
    @DisplayName("APPROVED + record.ext_data 空 → 抛 APPLICATION_INVALID")
    void missingExtDataThrows() {
        when(userBundleGrantDao.selectByApprovalId(42L)).thenReturn(List.of());
        when(userDirectBlockDao.selectByApprovalId(42L)).thenReturn(List.of());
        ApprovalRecord record = new ApprovalRecord();
        record.setId(42L);
        record.setExtData(null);
        when(approvalRecordDao.selectById(42L)).thenReturn(record);

        assertThatThrownBy(() -> integration.onFinalized(event(ApprovalFinalizedEvent.STATUS_APPROVED, 42L)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.APPLICATION_INVALID);
    }
}
