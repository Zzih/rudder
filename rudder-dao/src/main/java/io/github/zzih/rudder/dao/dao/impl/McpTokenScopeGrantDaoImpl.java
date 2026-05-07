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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.McpTokenScopeGrantDao;
import io.github.zzih.rudder.dao.entity.McpTokenScopeGrant;
import io.github.zzih.rudder.dao.mapper.McpTokenScopeGrantMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class McpTokenScopeGrantDaoImpl implements McpTokenScopeGrantDao {

    private final McpTokenScopeGrantMapper mapper;

    @Override
    public void batchInsert(List<McpTokenScopeGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return;
        }
        mapper.batchInsert(grants);
    }

    @Override
    public List<McpTokenScopeGrant> selectByTokenId(Long tokenId) {
        return mapper.queryByTokenId(tokenId);
    }

    @Override
    public List<McpTokenScopeGrant> selectActiveByTokenId(Long tokenId) {
        return mapper.queryActiveByTokenId(tokenId);
    }

    @Override
    public List<McpTokenScopeGrant> selectByApprovalId(Long approvalId) {
        return mapper.queryByApprovalId(approvalId);
    }

    @Override
    public int activateIfPending(Long id, Long activatedByUserId) {
        return mapper.activateIfPending(id, LocalDateTime.now(), activatedByUserId);
    }

    @Override
    public int rejectIfPending(Long id, Long rejectedByUserId, String rejectedReason) {
        return mapper.rejectIfPending(id, LocalDateTime.now(), rejectedByUserId, rejectedReason);
    }

    @Override
    public int revoke(Long id, String revokedReason) {
        return mapper.revoke(id, LocalDateTime.now(), revokedReason);
    }

    @Override
    public int revokeAllByTokenId(Long tokenId, String revokedReason) {
        return mapper.revokeAllByTokenId(tokenId, LocalDateTime.now(), revokedReason);
    }
}
