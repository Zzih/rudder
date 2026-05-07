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

package io.github.zzih.rudder.api.response;

import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.common.enums.approval.DecisionRule;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ApprovalRecordResponse {

    private Long id;
    private String channel;
    private String externalApprovalId;
    private String resourceType;
    private Long resourceCode;
    private Long workspaceId;
    private Long projectCode;
    private String title;
    private String description;
    private String submitRemark;
    private ApprovalStatus status;
    /** 阶段链（已 parse 为列表，前端直接用） */
    private List<String> stageChain;
    private String currentStage;
    private DecisionRule decisionRule;
    private Integer requiredCount;
    private LocalDateTime resolvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime withdrawnAt;
    private String withdrawnReason;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 决议历史（前端审计列表展示用） */
    private List<ApprovalDecisionResponse> decisions;
}
