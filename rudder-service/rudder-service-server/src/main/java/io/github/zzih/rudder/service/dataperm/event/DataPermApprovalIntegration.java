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

import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.ApprovalRecordDao;
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.approval.integration.ApprovalIntegration;
import io.github.zzih.rudder.service.dataperm.adapter.RangerAdapterRegistry;
import io.github.zzih.rudder.service.dataperm.adapter.RangerResourceAdapter;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermApplyContext;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.ResourcePathDTO;
import io.github.zzih.rudder.service.dataperm.reconciler.DataPermReconciler;
import io.github.zzih.rudder.service.dataperm.service.DataPermApplyService;
import io.github.zzih.rudder.service.dataperm.service.DataPermScopeAccessGroupService;
import io.github.zzih.rudder.service.dataperm.service.DataPermStatementSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据权限申请的审批终态处理:
 * <ul>
 *   <li>APPROVED → 解析 {@code t_r_approval_record.ext_data} 中的 {@link DataPermApplyContext}
 *       → **校验 role / datasource 仍存在**(P0-2)→ INSERT role_grants + direct_grants(全有或全无,事务原子)</li>
 *   <li>REJECTED / WITHDRAWN / EXPIRED → 业务表无操作</li>
 * </ul>
 *
 * <p>**幂等性**:Dispatcher 可能重试。落 grants 前先按 {@code source_approval_id} 查重,已存在直接返回。
 *
 * <p>**失效资源处理**:role 或 datasource 在审批期间被删 → 抛 {@link BizException},
 * 走 {@code ApprovalIntegrationDispatcher} 的失败链路(retry → 通知告警),申请人需重新申请。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermApprovalIntegration implements ApprovalIntegration {

    private final ApprovalRecordDao approvalRecordDao;
    private final DataPermBundleDao bundleDao;
    private final DataPermConfigService configService;
    private final DataPermScopeAccessGroupService accessGroupService;
    private final DataPermUserBundleGrantDao userBundleGrantDao;
    private final DataPermUserDirectGrantDao userDirectGrantDao;
    private final DataPermReconciler reconciler;
    private final RangerAdapterRegistry adapterRegistry;

    @Override
    public String resourceType() {
        return ApprovalResourceType.DATA_PERM_APPLY;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onFinalized(ApprovalFinalizedEvent event) {
        if (!event.isApproved()) {
            return;
        }
        // 重试幂等:dispatcher 失败重试时跳过已落 grants
        if (!userBundleGrantDao.selectByApprovalId(event.approvalId()).isEmpty()
                || !userDirectGrantDao.selectByApprovalId(event.approvalId()).isEmpty()) {
            log.info("DataPerm grants already exist for approvalId={}, skip", event.approvalId());
            return;
        }

        ApprovalRecord record = approvalRecordDao.selectById(event.approvalId());
        if (record == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "approvalRecord:" + event.approvalId());
        }
        DataPermApplyContext context = parseContext(record);

        // 失效资源校验: role / Ranger service 必须仍存在
        Set<Long> missingRoles = new HashSet<>();
        for (Long bundleId : context.bundleIds()) {
            if (bundleDao.selectById(bundleId) == null) {
                missingRoles.add(bundleId);
            }
        }
        Set<Long> missingScopes = new HashSet<>();
        for (DataPermStatementDTO block : context.directGrants()) {
            if (configService.findScope(block.getScopeCode()).isEmpty()) {
                missingScopes.add(block.getScopeCode());
            }
        }
        if (!missingRoles.isEmpty() || !missingScopes.isEmpty()) {
            throw new BizException(DataPermErrorCode.RESOURCE_MISSING,
                    "missingRoles=" + missingRoles + ", missingScopes=" + missingScopes);
        }
        // groupIds 是引用,申请到终审之间分组可能被删/改归属。不再校验会落出"已批准但 0 权限"的静默 grant,
        // 故此处重新校验存在 + 归属(申请期已校验过一次),失败走 dispatcher 失败链路而非静默放行。
        for (DataPermStatementDTO block : context.directGrants()) {
            accessGroupService.validateGroups(block.getScopeCode(), block.getGroupIds());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = context.expireAt();
        for (Long bundleId : context.bundleIds()) {
            DataPermUserBundleGrant grant = new DataPermUserBundleGrant();
            grant.setUserId(context.applicantUserId());
            grant.setSourceApprovalId(event.approvalId());
            grant.setBundleId(bundleId);
            grant.setEffectiveTime(now);
            grant.setExpirationTime(expireAt);
            userBundleGrantDao.insert(grant);
        }
        for (DataPermStatementDTO block : context.directGrants()) {
            DataPermUserDirectGrant entity = new DataPermUserDirectGrant();
            entity.setUserId(context.applicantUserId());
            entity.setSourceApprovalId(event.approvalId());
            entity.setScopeCode(block.getScopeCode());
            entity.setGroupIds(DataPermStatementSupport.toGroupIdsJson(block.getGroupIds()));
            entity.setEffectiveTime(now);
            entity.setExpirationTime(expireAt);
            Long statementId = userDirectGrantDao.insertStatement(entity);
            List<ResourceLevel> hierarchy = configService.findScope(block.getScopeCode())
                    .map(DataPermScopeDTO::getPluginType)
                    .flatMap(adapterRegistry::find)
                    .map(RangerResourceAdapter::resourceHierarchy)
                    .orElse(List.of());
            userDirectGrantDao.insertResources(statementId, toResourceEntities(hierarchy, block.getResources()));
        }
        log.info("DataPerm grants created: approvalId={}, applicantUserId={}, bundleGrants={}, directGrants={}",
                event.approvalId(), context.applicantUserId(),
                context.bundleIds().size(), context.directGrants().size());

        reconciler.triggerNowAfterCommit("APPROVAL_APPROVED:" + event.approvalId());
    }

    private static List<DataPermUserDirectGrantResource> toResourceEntities(List<ResourceLevel> hierarchy,
                                                                            List<ResourcePathDTO> resources) {
        List<DataPermUserDirectGrantResource> out = new ArrayList<>();
        if (resources == null) {
            return out;
        }
        for (ResourcePathDTO r : resources) {
            List<List<String>> canon = DataPermStatementSupport.canonicalizeForcedAll(hierarchy,
                    DataPermStatementSupport.normLevel(r.getCatalogNames()),
                    DataPermStatementSupport.normLevel(r.getDatabaseNames()),
                    DataPermStatementSupport.normLevel(r.getTableNames()),
                    DataPermStatementSupport.normLevel(r.getColumnNames()));
            DataPermUserDirectGrantResource e = new DataPermUserDirectGrantResource();
            e.setCatalogNames(DataPermStatementSupport.toJson(canon.get(0)));
            e.setDatabaseNames(DataPermStatementSupport.toJson(canon.get(1)));
            e.setTableNames(DataPermStatementSupport.toJson(canon.get(2)));
            e.setColumnNames(DataPermStatementSupport.toJson(canon.get(3)));
            out.add(e);
        }
        return out;
    }

    private static DataPermApplyContext parseContext(ApprovalRecord record) {
        String extData = record.getExtData();
        if (extData == null || extData.isBlank()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "approvalRecord.extData empty for id=" + record.getId());
        }
        Map<String, String> extra = JsonUtils.fromJson(extData, new TypeReference<>() {
        });
        if (extra == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "approvalRecord.extData not a JSON map");
        }
        String contextJson = extra.get(DataPermApplyService.EXTRA_KEY_DATA_PERM);
        if (contextJson == null || contextJson.isBlank()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "approvalRecord.extData missing key " + DataPermApplyService.EXTRA_KEY_DATA_PERM);
        }
        DataPermApplyContext context = JsonUtils.fromJson(contextJson, DataPermApplyContext.class);
        if (context == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID,
                    "approvalRecord.extData context unparseable");
        }
        return context;
    }
}
