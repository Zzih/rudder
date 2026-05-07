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

import io.github.zzih.rudder.common.enums.approval.DecisionType;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 审批决议（每条决议一行，多审批人留痕）。
 *
 * <p>每个候选审批人对某审批单某阶段的一次表态。UNIQUE (approval_id, stage, decider_user_id)
 * 防止同一人同阶段重复决议。
 */
@Data
@TableName("t_r_approval_decision")
public class ApprovalDecision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long approvalId;

    private String stage;

    private Long deciderUserId;

    private String deciderUsername;

    private DecisionType decision;

    private LocalDateTime decidedAt;

    private String remark;

    private LocalDateTime createdAt;
}
