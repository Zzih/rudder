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

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantDetailView;
import io.github.zzih.rudder.dao.projection.UserDirectGrantOverviewRow;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface DataPermUserDirectGrantDao {

    int insert(DataPermUserDirectGrant grant);

    DataPermUserDirectGrant selectById(Long id);

    List<DataPermUserDirectGrantDetailView> selectActiveByUser(Long userId, LocalDateTime asOf);

    /** 批量拉多用户的 active direct grants,reconciler 单轮一次 SQL 替代 N 次;LEFT JOIN scope 出元数据。 */
    List<DataPermUserDirectGrantDetailView> selectActiveByUserIds(java.util.Collection<Long> userIds,
                                                                  LocalDateTime asOf);

    /** "我的权限" Direct 概览:1 次聚合返回,无 grant 时返 null。 */
    UserDirectGrantOverviewRow selectActiveOverviewByUser(Long userId, LocalDateTime asOf);

    /** Direct 卡片展开后翻页拉行,LEFT JOIN scope 直出 scopeName。 */
    IPage<DataPermUserDirectGrantDetailView> pageActiveByUser(Long userId, LocalDateTime asOf,
                                                              int pageNum, int pageSize);

    List<DataPermUserDirectGrantDetailView> selectInactiveByUser(Long userId, LocalDateTime now);

    List<DataPermUserDirectGrant> selectByApprovalId(Long approvalId);

    int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote);

    List<Long> selectDistinctActiveUserIds(LocalDateTime asOf);

    /** 计算引用某 Ranger Service 的 direct grant 数(校验 service 是否仍被引用时用)。 */
    long countByScopeCode(Long scopeCode);
}
