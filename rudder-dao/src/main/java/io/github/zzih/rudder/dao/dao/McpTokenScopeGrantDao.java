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

import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;

import java.util.List;

public interface McpTokenScopeGrantDao {

    void batchInsert(List<McpTokenScopeGrant> grants);

    List<McpTokenScopeGrant> selectByTokenId(Long tokenId);

    /** 双闸门 scope 闸用：仅返回 ACTIVE 状态的 grant。 */
    List<McpTokenScopeGrant> selectActiveByTokenId(Long tokenId);

    List<McpTokenScopeGrant> selectByApprovalId(Long approvalId);

    /**
     * 审批通过：仅 PENDING_APPROVAL 才生效。
     *
     * @return 影响行数；0 表示已被处理过
     */
    int activateIfPending(Long id, Long activatedByUserId);

    /** 审批拒绝：仅 PENDING_APPROVAL 才生效。 */
    int rejectIfPending(Long id, Long rejectedByUserId, String rejectedReason);

    /** 撤销单个 grant（角色降级 / 用户撤回 grant）。 */
    int revoke(Long id, String revokedReason);

    /** 整 token 撤销时级联撤销所有 grants。 */
    int revokeAllByTokenId(Long tokenId, String revokedReason);
}
