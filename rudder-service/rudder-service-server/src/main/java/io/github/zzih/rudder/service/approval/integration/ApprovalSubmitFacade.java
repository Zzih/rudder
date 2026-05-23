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

package io.github.zzih.rudder.service.approval.integration;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.workflow.ApprovalService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 业务模块提交审批的统一入口。按 {@code resourceType} 找 {@link ApprovalIntegration},
 * 让它构造 {@link ApprovalRequest},再交给现有 {@link ApprovalService#submit} 完整链路。
 *
 * <p>避免业务模块各自重复 "build request + 调 submit" 胶水代码。
 */
@Slf4j
@Service
public class ApprovalSubmitFacade {

    private final Map<String, ApprovalIntegration> byType;
    private final ApprovalService approvalService;

    public ApprovalSubmitFacade(List<ApprovalIntegration> integrations, ApprovalService approvalService) {
        this.byType = integrations.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ApprovalIntegration::resourceType,
                        Function.identity()));
        this.approvalService = approvalService;
    }

    /**
     * 提交审批。
     *
     * @param resourceType 必须有对应 {@link ApprovalIntegration} 注册
     * @param resourceCode 业务实体 ID,传给 {@link ApprovalIntegration#buildRequest}
     * @param workspaceId  关联工作空间 ID(决定数据权限可见性,传给 ApprovalService)
     * @param projectCode  关联项目 ID(可空,工作流/项目发布相关)
     * @param submitRemark 提交备注(可空)
     * @return 审批单 id ({@code t_r_approval_record.id})
     */
    public Long submit(String resourceType,
                       Long resourceCode,
                       Long workspaceId,
                       Long projectCode,
                       String submitRemark) {
        ApprovalIntegration integration = byType.get(resourceType);
        if (integration == null) {
            throw new BizException(DataPermErrorCode.INTEGRATION_NOT_FOUND, resourceType);
        }
        ApprovalRequest request = integration.buildRequest(resourceCode);
        return approvalService.submit(request, resourceType, resourceCode, workspaceId, projectCode, submitRemark);
    }
}
