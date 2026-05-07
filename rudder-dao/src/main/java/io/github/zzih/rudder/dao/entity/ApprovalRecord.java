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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.entity.BaseEntity;
import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.common.enums.approval.DecisionRule;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_approval_record")
public class ApprovalRecord extends BaseEntity {

    private String channel;

    private String externalApprovalId;

    private String resourceType;

    private Long resourceCode;

    private Long workspaceId;

    private Long projectCode;

    private String title;

    private String description;

    private String submitRemark;

    /** PENDING / APPROVED / REJECTED / WITHDRAWN / EXPIRED */
    private ApprovalStatus status;

    /** 阶段链 JSON 数组（提交时锁定），如 ["PROJECT_OWNER","WORKSPACE_OWNER"] */
    private String stageChain;

    /** 当前所在阶段，必为 stageChain 中的一项 */
    private String currentStage;

    private DecisionRule decisionRule;

    private Integer requiredCount;

    private LocalDateTime resolvedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime withdrawnAt;

    private String withdrawnReason;

    /**
     * 解析 {@link #stageChain} JSON 数组为 {@code List<String>}。
     * 命名故意不以 "get" 开头，避免被 Jackson / Lombok 当作 bean 属性序列化进 ORM。
     * 空 / 解析失败返回空列表。
     */
    public List<String> parseStageChainList() {
        return JsonUtils.toList(stageChain, String.class);
    }
}
