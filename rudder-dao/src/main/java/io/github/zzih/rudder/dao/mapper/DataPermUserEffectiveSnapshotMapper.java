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

import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.projection.EffectiveSnapshotRow;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermUserEffectiveSnapshotMapper extends BaseMapper<DataPermUserEffectiveSnapshot> {

    Long findMaxVersion(@Param("userId") Long userId);

    Long findMaxVersionAt(@Param("userId") Long userId, @Param("asOf") LocalDateTime asOf);

    List<DataPermUserEffectiveSnapshot> queryByUserAndVersion(@Param("userId") Long userId,
                                                              @Param("version") Long version);

    int insertBatch(@Param("rows") List<DataPermUserEffectiveSnapshot> rows);

    /** 最新 version 仍有非 sentinel 行的 user id 列表。Reconciler 用此并入本轮迭代,撤光 user 立即写 sentinel。 */
    List<Long> selectUserIdsWithActiveSnapshot();

    /** "全部用户权限" 列表:取每 user 最新(或 asOf 之前最新)version 的 snapshot 行,带过滤分页。 */
    IPage<EffectiveSnapshotRow> queryEffectiveSnapshot(IPage<EffectiveSnapshotRow> page,
                                                       @Param("userIds") List<Long> userIds,
                                                       @Param("scopeCodes") List<Long> scopeCodes,
                                                       @Param("keyword") String keyword,
                                                       @Param("asOf") LocalDateTime asOf,
                                                       @Param("workspaceId") Long workspaceId);
}
