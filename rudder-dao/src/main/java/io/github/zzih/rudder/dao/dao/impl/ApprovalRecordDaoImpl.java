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

import io.github.zzih.rudder.dao.dao.ApprovalRecordDao;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.mapper.ApprovalRecordMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ApprovalRecordDaoImpl implements ApprovalRecordDao {

    private final ApprovalRecordMapper approvalRecordMapper;

    @Override
    public IPage<ApprovalRecord> selectPage(int pageNum, int pageSize, String status,
                                            Long userId, int roleLevel, Long workspaceId) {
        return approvalRecordMapper.queryPage(new Page<>(pageNum, pageSize), status,
                userId, roleLevel, workspaceId);
    }

    @Override
    public List<ApprovalRecord> selectByResourceTypeAndResourceCode(String resourceType, Long resourceCode) {
        return approvalRecordMapper.queryByResourceTypeAndResourceCode(resourceType, resourceCode);
    }

    @Override
    public ApprovalRecord selectById(Long id) {
        return approvalRecordMapper.selectById(id);
    }

    @Override
    public ApprovalRecord selectByExternalApprovalId(String externalApprovalId) {
        return approvalRecordMapper.queryByExternalApprovalId(externalApprovalId);
    }

    @Override
    public int insert(ApprovalRecord record) {
        return approvalRecordMapper.insert(record);
    }

    @Override
    public int updateById(ApprovalRecord record) {
        return approvalRecordMapper.updateById(record);
    }

    @Override
    public int advanceStageIfPending(Long id, String oldStage, String newStage) {
        return approvalRecordMapper.advanceStageIfPending(id, oldStage, newStage);
    }

    @Override
    public int finalizeIfPending(Long id, String status) {
        return approvalRecordMapper.finalizeIfPending(id, status, LocalDateTime.now());
    }

    @Override
    public int withdrawIfPending(Long id, Long ownerId, String reason) {
        return approvalRecordMapper.withdrawIfPending(id, ownerId, LocalDateTime.now(), reason);
    }

    @Override
    public List<Long> selectExpiredPendingIds(int limit) {
        return approvalRecordMapper.queryExpiredPendingIds(LocalDateTime.now(), limit);
    }
}
