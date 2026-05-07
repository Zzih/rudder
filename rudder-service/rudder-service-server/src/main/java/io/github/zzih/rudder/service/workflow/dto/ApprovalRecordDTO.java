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

package io.github.zzih.rudder.service.workflow.dto;

import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.common.enums.approval.DecisionRule;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ApprovalRecordDTO {

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
    /** JSON 数组字符串：阶段链 */
    private String stageChain;
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
}
