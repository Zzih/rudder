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

import io.github.zzih.rudder.dao.dao.DataPermUserEffectiveSnapshotDao;
import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.mapper.DataPermUserEffectiveSnapshotMapper;
import io.github.zzih.rudder.dao.projection.EffectiveSnapshotRow;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermUserEffectiveSnapshotDaoImpl implements DataPermUserEffectiveSnapshotDao {

    private final DataPermUserEffectiveSnapshotMapper mapper;

    @Override
    public Long findMaxVersion(Long userId) {
        return mapper.findMaxVersion(userId);
    }

    @Override
    public List<DataPermUserEffectiveSnapshot> selectLatest(Long userId) {
        Long v = mapper.findMaxVersion(userId);
        if (v == null) {
            return List.of();
        }
        return mapper.queryByUserAndVersion(userId, v);
    }

    @Override
    public List<DataPermUserEffectiveSnapshot> selectAt(Long userId, LocalDateTime asOf) {
        Long v = mapper.findMaxVersionAt(userId, asOf);
        if (v == null) {
            return List.of();
        }
        return mapper.queryByUserAndVersion(userId, v);
    }

    @Override
    public java.util.Set<Long> selectUserIdsWithActiveSnapshot() {
        return java.util.Set.copyOf(mapper.selectUserIdsWithActiveSnapshot());
    }

    @Override
    public int insertBatch(List<DataPermUserEffectiveSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        return mapper.insertBatch(rows);
    }

    @Override
    public IPage<EffectiveSnapshotRow> pageEffectiveSnapshot(List<Long> userIds, List<Long> scopeCodes,
                                                             String keyword, LocalDateTime asOf,
                                                             Long workspaceId,
                                                             int pageNum, int pageSize) {
        return mapper.queryEffectiveSnapshot(new Page<>(pageNum, pageSize),
                userIds, scopeCodes, keyword, asOf, workspaceId);
    }
}
