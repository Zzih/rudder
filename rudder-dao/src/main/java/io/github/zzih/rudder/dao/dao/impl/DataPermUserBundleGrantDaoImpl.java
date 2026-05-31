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

import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserBundleGrantDetailView;
import io.github.zzih.rudder.dao.mapper.DataPermUserBundleGrantMapper;
import io.github.zzih.rudder.dao.projection.UserBundleGrantSummaryRow;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermUserBundleGrantDaoImpl implements DataPermUserBundleGrantDao {

    private final DataPermUserBundleGrantMapper mapper;

    @Override
    public int insert(DataPermUserBundleGrant grant) {
        return mapper.insert(grant);
    }

    @Override
    public DataPermUserBundleGrant selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DataPermUserBundleGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserBundleGrant> selectActiveByUserIds(java.util.Collection<Long> userIds,
                                                               LocalDateTime asOf) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.List.of();
        }
        return mapper.queryActiveByUserIds(userIds, asOf);
    }

    @Override
    public List<UserBundleGrantSummaryRow> selectActiveSummaryByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveSummaryByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserBundleGrantDetailView> selectInactiveByUser(Long userId, LocalDateTime now) {
        return mapper.queryInactiveByUser(userId, now);
    }

    @Override
    public Map<Long, Long> countActiveByBundleIds(List<Long> bundleIds, LocalDateTime asOf) {
        if (bundleIds == null || bundleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> row : mapper.queryActiveCountByBundleIds(bundleIds, asOf)) {
            out.put(((Number) row.get("bundleId")).longValue(),
                    ((Number) row.get("cnt")).longValue());
        }
        return out;
    }

    @Override
    public long countActiveByBundleId(Long bundleId, LocalDateTime asOf) {
        return countActiveByBundleIds(List.of(bundleId), asOf).getOrDefault(bundleId, 0L);
    }

    @Override
    public List<DataPermUserBundleGrant> selectByApprovalId(Long approvalId) {
        return mapper.queryByApprovalId(approvalId);
    }

    @Override
    public int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote) {
        return mapper.expireIfActive(id, now, endReason, endBy, endNote);
    }

    @Override
    public int expireAllByBundleId(Long bundleId, LocalDateTime now, String endReason) {
        return mapper.expireAllByBundleId(bundleId, now, endReason);
    }

    @Override
    public List<Long> selectDistinctActiveUserIds(LocalDateTime asOf) {
        return mapper.queryDistinctActiveUserIds(asOf);
    }
}
