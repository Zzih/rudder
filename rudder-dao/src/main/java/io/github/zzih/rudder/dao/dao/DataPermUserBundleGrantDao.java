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

import io.github.zzih.rudder.dao.entity.DataPermUserBundleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserBundleGrantDetailView;
import io.github.zzih.rudder.dao.projection.InactiveGrantRef;
import io.github.zzih.rudder.dao.projection.UserBundleGrantSummaryRow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface DataPermUserBundleGrantDao {

    int insert(DataPermUserBundleGrant grant);

    DataPermUserBundleGrant selectById(Long id);

    List<DataPermUserBundleGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf);

    /** 批量拉多用户的 active role grants,Reconciler 单轮一次 SQL 替代 N 次。 */
    List<DataPermUserBundleGrant> selectActiveByUserIds(java.util.Collection<Long> userIds, LocalDateTime asOf);

    /** "我的权限" 列表骨架:1 次 SQL,按 bundleId 聚合 + role.name + perm_count。 */
    List<UserBundleGrantSummaryRow> selectActiveSummaryByUser(Long userId, LocalDateTime asOf);

    /** 跨 role / direct 两表按失效时间统一分页出 (id, kind) 引用。 */
    IPage<InactiveGrantRef> selectInactiveRefsPage(Long userId, LocalDateTime now, int pageNum, int pageSize);

    /** 按 id 批量拉 role grant 明细视图(历史分页补全用)。 */
    List<DataPermUserBundleGrantDetailView> selectByIds(Collection<Long> ids);

    /** 单条 SQL 批量统计多个 role 在 asOf 时活跃的独立用户数(COUNT DISTINCT user_id),避免按 bundleId 循环 SELECT。 */
    java.util.Map<Long, Long> countActiveByBundleIds(List<Long> bundleIds, LocalDateTime asOf);

    /** 单 role 在 asOf 时活跃的独立用户数。 */
    long countActiveByBundleId(Long bundleId, LocalDateTime asOf);

    List<DataPermUserBundleGrant> selectByApprovalId(Long approvalId);

    int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote);

    int expireAllByBundleId(Long bundleId, LocalDateTime now, String endReason);

    List<Long> selectDistinctActiveUserIds(LocalDateTime asOf);
}
