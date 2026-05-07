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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface McpTokenScopeGrantMapper extends BaseMapper<McpTokenScopeGrant> {

    int batchInsert(@Param("list") List<McpTokenScopeGrant> grants);

    List<McpTokenScopeGrant> queryByTokenId(@Param("tokenId") Long tokenId);

    /** 双闸门读取：仅返回 ACTIVE 状态的 grant（scope 闸门用）。 */
    List<McpTokenScopeGrant> queryActiveByTokenId(@Param("tokenId") Long tokenId);

    List<McpTokenScopeGrant> queryByApprovalId(@Param("approvalId") Long approvalId);

    /**
     * 审批通过激活：仅 PENDING_APPROVAL 才生效。
     */
    int activateIfPending(@Param("id") Long id,
                          @Param("activatedAt") LocalDateTime activatedAt,
                          @Param("activatedByUserId") Long activatedByUserId);

    /** 审批拒绝：仅 PENDING_APPROVAL 才生效。 */
    int rejectIfPending(@Param("id") Long id,
                        @Param("rejectedAt") LocalDateTime rejectedAt,
                        @Param("rejectedByUserId") Long rejectedByUserId,
                        @Param("rejectedReason") String rejectedReason);

    /** 撤销：当前态非 REVOKED 即生效（角色降级 / token 撤销 / 用户撤回都走这条）。 */
    int revoke(@Param("id") Long id,
               @Param("revokedAt") LocalDateTime revokedAt,
               @Param("revokedReason") String revokedReason);

    /** 批量按 token_id 撤销（token 整体被 revoke 时级联）。 */
    int revokeAllByTokenId(@Param("tokenId") Long tokenId,
                           @Param("revokedAt") LocalDateTime revokedAt,
                           @Param("revokedReason") String revokedReason);
}
