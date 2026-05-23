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

import io.github.zzih.rudder.dao.dao.DataPermUserDirectGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantDetailView;
import io.github.zzih.rudder.dao.mapper.DataPermUserDirectGrantMapper;
import io.github.zzih.rudder.dao.projection.UserDirectGrantOverviewRow;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermUserDirectGrantDaoImpl implements DataPermUserDirectGrantDao {

    private final DataPermUserDirectGrantMapper mapper;

    @Override
    public int insert(DataPermUserDirectGrant grant) {
        return mapper.insert(grant);
    }

    @Override
    public DataPermUserDirectGrant selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DataPermUserDirectGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserDirectGrantDetailView> selectActiveByUserIds(java.util.Collection<Long> userIds,
                                                                         LocalDateTime asOf) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.List.of();
        }
        return mapper.queryActiveByUserIds(userIds, asOf);
    }

    @Override
    public UserDirectGrantOverviewRow selectActiveOverviewByUser(Long userId, LocalDateTime asOf) {
        return mapper.queryActiveOverviewByUser(userId, asOf);
    }

    @Override
    public IPage<DataPermUserDirectGrantDetailView> pageActiveByUser(Long userId, LocalDateTime asOf,
                                                                     int pageNum, int pageSize) {
        return mapper.pageActiveByUser(new Page<>(pageNum, pageSize), userId, asOf);
    }

    @Override
    public List<DataPermUserDirectGrantDetailView> selectInactiveByUser(Long userId, LocalDateTime now) {
        return mapper.queryInactiveByUser(userId, now);
    }

    @Override
    public List<DataPermUserDirectGrant> selectByApprovalId(Long approvalId) {
        return mapper.queryByApprovalId(approvalId);
    }

    @Override
    public int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote) {
        return mapper.expireIfActive(id, now, endReason, endBy, endNote);
    }

    @Override
    public List<Long> selectDistinctActiveUserIds(LocalDateTime asOf) {
        return mapper.queryDistinctActiveUserIds(asOf);
    }

    @Override
    public long countByScopeCode(Long scopeCode) {
        return mapper.countByScopeCode(scopeCode);
    }
}
