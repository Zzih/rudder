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

import io.github.zzih.rudder.dao.dao.DataPermBundleStatementDao;
import io.github.zzih.rudder.dao.entity.DataPermBundleStatement;
import io.github.zzih.rudder.dao.entity.DataPermBundleStatementResource;
import io.github.zzih.rudder.dao.entity.view.DataPermBundleStatementResourceView;
import io.github.zzih.rudder.dao.mapper.DataPermBundleStatementMapper;
import io.github.zzih.rudder.dao.mapper.DataPermBundleStatementResourceMapper;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

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
public class DataPermBundleStatementDaoImpl implements DataPermBundleStatementDao {

    private final DataPermBundleStatementMapper blockMapper;
    private final DataPermBundleStatementResourceMapper resourceMapper;

    @Override
    public DataPermBundleStatement selectStatementById(Long id) {
        return id == null ? null : blockMapper.selectById(id);
    }

    @Override
    public IPage<DataPermBundleStatement> pageStatements(Long bundleId, String keyword, int pageNum, int pageSize) {
        return blockMapper.queryPageByBundle(new Page<>(pageNum, pageSize), bundleId, keyword);
    }

    @Override
    public List<DataPermBundleStatementResourceView> selectResourceViewsByBundleIds(Collection<Long> bundleIds) {
        return bundleIds == null || bundleIds.isEmpty() ? List.of() : resourceMapper.queryViewsByBundleIds(bundleIds);
    }

    @Override
    public List<DataPermBundleStatementResourceView> selectResourceViewsByStatementIds(Collection<Long> statementIds) {
        return statementIds == null || statementIds.isEmpty() ? List.of()
                : resourceMapper.queryViewsByStatementIds(statementIds);
    }

    @Override
    public IPage<GrantItemRow> pageFlattenedByBundle(Long bundleId, int pageNum, int pageSize) {
        Page<GrantItemRow> page = new Page<>(pageNum, pageSize);
        page.setSearchCount(false);
        page.setRecords(resourceMapper.queryPageFlattenedByBundle(page, bundleId));
        page.setTotal(resourceMapper.sumFlattenedByBundle(bundleId));
        return page;
    }

    @Override
    public Long insertStatement(DataPermBundleStatement block) {
        blockMapper.insert(block);
        return block.getId();
    }

    @Override
    public int updateStatement(DataPermBundleStatement block) {
        return blockMapper.updateById(block);
    }

    @Override
    public void replaceResources(Long statementId, List<DataPermBundleStatementResource> resources) {
        resourceMapper.deleteByStatementId(statementId);
        if (resources != null) {
            for (DataPermBundleStatementResource r : resources) {
                r.setId(null);
                r.setStatementId(statementId);
                resourceMapper.insert(r);
            }
        }
    }

    @Override
    public int deleteStatement(Long statementId) {
        resourceMapper.deleteByStatementId(statementId);
        return blockMapper.deleteById(statementId);
    }

    @Override
    public int deleteByBundleId(Long bundleId) {
        for (DataPermBundleStatement b : blockMapper.queryByBundleId(bundleId)) {
            resourceMapper.deleteByStatementId(b.getId());
        }
        return blockMapper.deleteByBundleId(bundleId);
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
