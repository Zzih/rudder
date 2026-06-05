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
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantResourceView;
import io.github.zzih.rudder.dao.mapper.DataPermUserDirectGrantMapper;
import io.github.zzih.rudder.dao.mapper.DataPermUserDirectGrantResourceMapper;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

/**
 * 块与其库表行是同一聚合根:库表行随块级联建删,不存在独立于块的生命周期,故两张表的 mapper
 * 由本 dao 统一持有,把级联次序收口在一处(对"一 dao 一表"惯例的有意例外)。
 */
@Repository
@RequiredArgsConstructor
public class DataPermUserDirectGrantDaoImpl implements DataPermUserDirectGrantDao {

    private final DataPermUserDirectGrantMapper blockMapper;
    private final DataPermUserDirectGrantResourceMapper resourceMapper;

    @Override
    public DataPermUserDirectGrant selectById(Long id) {
        return id == null ? null : blockMapper.selectById(id);
    }

    @Override
    public Long insertStatement(DataPermUserDirectGrant block) {
        blockMapper.insert(block);
        return block.getId();
    }

    @Override
    public void insertResources(Long statementId, List<DataPermUserDirectGrantResource> resources) {
        if (resources == null) {
            return;
        }
        for (DataPermUserDirectGrantResource r : resources) {
            r.setId(null);
            r.setStatementId(statementId);
            resourceMapper.insert(r);
        }
    }

    @Override
    public List<DataPermUserDirectGrant> selectActiveByUser(Long userId, LocalDateTime asOf) {
        return blockMapper.queryActiveByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserDirectGrantResource> selectResourcesByStatementIds(Collection<Long> statementIds) {
        return statementIds == null || statementIds.isEmpty() ? List.of()
                : resourceMapper.queryByStatementIds(statementIds);
    }

    @Override
    public List<DataPermUserDirectGrantResourceView> selectActiveResourceViewsByUserIds(
                                                                                        Collection<Long> userIds,
                                                                                        LocalDateTime asOf) {
        return userIds == null || userIds.isEmpty()
                ? List.of()
                : resourceMapper.queryActiveViewsByUserIds(userIds, asOf);
    }

    @Override
    public IPage<GrantItemRow> pageActiveFlattenedByUser(Long userId, LocalDateTime asOf, int pageNum, int pageSize) {
        Page<GrantItemRow> page = new Page<>(pageNum, pageSize);
        page.setSearchCount(false);
        page.setRecords(resourceMapper.queryPageActiveFlattenedByUser(page, userId, asOf));
        page.setTotal(resourceMapper.sumFlattenedActiveByUser(userId, asOf));
        return page;
    }

    @Override
    public long sumFlattenedActiveByUser(Long userId, LocalDateTime asOf) {
        return resourceMapper.sumFlattenedActiveByUser(userId, asOf);
    }

    @Override
    public List<DataPermUserDirectGrant> selectByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : blockMapper.queryByIds(ids);
    }

    @Override
    public List<DataPermUserDirectGrant> selectByApprovalId(Long approvalId) {
        return blockMapper.queryByApprovalId(approvalId);
    }

    @Override
    public int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote) {
        return blockMapper.expireIfActive(id, now, endReason, endBy, endNote);
    }

    @Override
    public List<Long> selectDistinctActiveUserIds(LocalDateTime asOf) {
        return blockMapper.queryDistinctActiveUserIds(asOf);
    }

    @Override
    public long countByScopeCode(Long scopeCode) {
        return scopeCode == null ? 0L : blockMapper.countByScopeCode(scopeCode);
    }

    @Override
    public long countByGroupId(Long groupId) {
        return groupId == null ? 0L : blockMapper.countByGroupId(groupId);
    }
}
