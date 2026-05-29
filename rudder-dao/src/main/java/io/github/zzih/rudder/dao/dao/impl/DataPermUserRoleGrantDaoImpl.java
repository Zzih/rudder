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

import io.github.zzih.rudder.dao.dao.DataPermUserRoleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermUserRoleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserRoleGrantDetailView;
import io.github.zzih.rudder.dao.mapper.DataPermUserRoleGrantMapper;
import io.github.zzih.rudder.dao.projection.UserRoleGrantSummaryRow;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermUserRoleGrantDaoImpl implements DataPermUserRoleGrantDao {

    private final DataPermUserRoleGrantMapper mapper;

    @Override
    public int insert(DataPermUserRoleGrant grant) {
        return mapper.insert(grant);
    }

    @Override
    public DataPermUserRoleGrant selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DataPermUserRoleGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserRoleGrant> selectActiveByUserIds(java.util.Collection<Long> userIds,
                                                             LocalDateTime asOf) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.List.of();
        }
        return mapper.queryActiveByUserIds(userIds, asOf);
    }

    @Override
    public List<UserRoleGrantSummaryRow> selectActiveSummaryByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveSummaryByUser(userId, asOf);
    }

    @Override
    public boolean existsActiveByUserAndRole(Long userId, Long roleId, LocalDateTime asOf) {
        return mapper.existsActiveByUserAndRole(userId, roleId, asOf) != null;
    }

    @Override
    public List<DataPermUserRoleGrantDetailView> selectInactiveByUser(Long userId, LocalDateTime now) {
        return mapper.queryInactiveByUser(userId, now);
    }

    @Override
    public Map<Long, Long> countActiveByRoleIds(List<Long> roleIds, LocalDateTime asOf) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> row : mapper.queryActiveCountByRoleIds(roleIds, asOf)) {
            out.put(((Number) row.get("roleId")).longValue(),
                    ((Number) row.get("cnt")).longValue());
        }
        return out;
    }

    @Override
    public long countActiveByRoleId(Long roleId, LocalDateTime asOf) {
        return countActiveByRoleIds(List.of(roleId), asOf).getOrDefault(roleId, 0L);
    }

    @Override
    public List<DataPermUserRoleGrant> selectByApprovalId(Long approvalId) {
        return mapper.queryByApprovalId(approvalId);
    }

    @Override
    public int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote) {
        return mapper.expireIfActive(id, now, endReason, endBy, endNote);
    }

    @Override
    public int expireAllByRoleId(Long roleId, LocalDateTime now, String endReason) {
        return mapper.expireAllByRoleId(roleId, now, endReason);
    }

    @Override
    public List<Long> selectDistinctActiveUserIds(LocalDateTime asOf) {
        return mapper.queryDistinctActiveUserIds(asOf);
    }
}
