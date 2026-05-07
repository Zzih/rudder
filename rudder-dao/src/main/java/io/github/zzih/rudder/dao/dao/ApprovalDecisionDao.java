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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.ApprovalDecision;

import java.util.List;

public interface ApprovalDecisionDao {

    /**
     * 插入决议。UNIQUE (approval_id, stage, decider_user_id) 冲突时抛
     * org.springframework.dao.DuplicateKeyException — 上层视为幂等决议。
     */
    int insert(ApprovalDecision decision);

    List<ApprovalDecision> selectByApprovalId(Long approvalId);

    List<ApprovalDecision> selectByApprovalIdAndStage(Long approvalId, String stage);

    int countApproveByStage(Long approvalId, String stage);

    List<ApprovalDecision> selectByDeciderUserId(Long deciderUserId);
}
