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

import io.github.zzih.rudder.dao.entity.DataPermUserRoleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserRoleGrantDetailView;
import io.github.zzih.rudder.dao.projection.UserRoleGrantSummaryRow;

import java.time.LocalDateTime;
import java.util.List;

public interface DataPermUserRoleGrantDao {

    int insert(DataPermUserRoleGrant grant);

    DataPermUserRoleGrant selectById(Long id);

    List<DataPermUserRoleGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf);

    /** 批量拉多用户的 active role grants,Reconciler 单轮一次 SQL 替代 N 次。 */
    List<DataPermUserRoleGrant> selectActiveByUserIds(java.util.Collection<Long> userIds, LocalDateTime asOf);

    /** "我的权限" 列表骨架:1 次 SQL,按 roleId 聚合 + role.name + perm_count。 */
    List<UserRoleGrantSummaryRow> selectActiveSummaryByUser(Long userId, LocalDateTime asOf);

    /** 单 (user, role) 在 asOf 时是否仍有 active grant。 */
    boolean existsActiveByUserAndRole(Long userId, Long roleId, LocalDateTime asOf);

    /** 已失效(expiration_time IS NOT NULL 且 <= now)的全部 role grants —— 「我的权限」历史折叠区用。 */
    List<DataPermUserRoleGrantDetailView> selectInactiveByUser(Long userId, LocalDateTime now);

    /** 单条 SQL 批量统计多个 role 在 asOf 时活跃的独立用户数(COUNT DISTINCT user_id),避免按 roleId 循环 SELECT。 */
    java.util.Map<Long, Long> countActiveByRoleIds(List<Long> roleIds, LocalDateTime asOf);

    /** 单 role 在 asOf 时活跃的独立用户数。 */
    long countActiveByRoleId(Long roleId, LocalDateTime asOf);

    List<DataPermUserRoleGrant> selectByApprovalId(Long approvalId);

    int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote);

    int expireAllByRoleId(Long roleId, LocalDateTime now, String endReason);

    List<Long> selectDistinctActiveUserIds(LocalDateTime asOf);
}
