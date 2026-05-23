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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.DataPermUserEffectiveSnapshot;
import io.github.zzih.rudder.dao.projection.EffectiveSnapshotRow;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface DataPermUserEffectiveSnapshotDao {

    /** 当前最新版本号;无快照返回 null。 */
    Long findMaxVersion(Long userId);

    /** 查 user 最新版本的所有 perm 行;无快照返回空列表。 */
    List<DataPermUserEffectiveSnapshot> selectLatest(Long userId);

    /** 查 user 在 asOf 时刻的有效快照(最大 version where snapshot_time ≤ asOf)。 */
    List<DataPermUserEffectiveSnapshot> selectAt(Long userId, LocalDateTime asOf);

    /** 最新 version 仍有非 sentinel 行(有效权限)的 user id 集合。Reconciler 用此并入本轮迭代。 */
    java.util.Set<Long> selectUserIdsWithActiveSnapshot();

    /** 批量插入一份新 version 的所有 perm 行(共享 version + snapshot_time)。 */
    int insertBatch(List<DataPermUserEffectiveSnapshot> rows);

    /** "数据权限总览" 列表分页:取每 user 最新(asOf 之前最新)version 的 snapshot 行。
     *  workspaceId 非空时按 workspace_member 限定到该空间成员,实现普通用户视角的 audit 范围控制。 */
    IPage<EffectiveSnapshotRow> pageEffectiveSnapshot(List<Long> userIds, List<Long> scopeCodes,
                                                      String keyword, LocalDateTime asOf,
                                                      Long workspaceId,
                                                      int pageNum, int pageSize);
}
