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

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantDetailView;
import io.github.zzih.rudder.dao.projection.UserDirectGrantOverviewRow;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermUserDirectGrantMapper extends BaseMapper<DataPermUserDirectGrant> {

    /** 查 user 在某时间点处于"有效"状态的所有 direct grants;LEFT JOIN scope 出 scopeName。 */
    List<DataPermUserDirectGrantDetailView> queryActiveByUser(@Param("userId") Long userId,
                                                              @Param("asOf") LocalDateTime asOf);

    /** 批量版本,reconciler 一次拉全部 active user 的 direct grants;LEFT JOIN scope 出 scope 元数据。 */
    List<DataPermUserDirectGrantDetailView> queryActiveByUserIds(@Param("userIds") java.util.Collection<Long> userIds,
                                                                 @Param("asOf") LocalDateTime asOf);

    /** "我的权限" Direct 概览:min(effective)、合并 expiration、count(*) 1 行返回(无数据时返 null)。 */
    UserDirectGrantOverviewRow queryActiveOverviewByUser(@Param("userId") Long userId,
                                                         @Param("asOf") LocalDateTime asOf);

    /** Direct 卡片展开后翻页拉行;LEFT JOIN scope 直出 scopeName。 */
    IPage<DataPermUserDirectGrantDetailView> pageActiveByUser(IPage<DataPermUserDirectGrantDetailView> page,
                                                              @Param("userId") Long userId,
                                                              @Param("asOf") LocalDateTime asOf);

    List<DataPermUserDirectGrantDetailView> queryInactiveByUser(@Param("userId") Long userId,
                                                                @Param("now") LocalDateTime now);

    List<DataPermUserDirectGrant> queryByApprovalId(@Param("approvalId") Long approvalId);

    int expireIfActive(@Param("id") Long id,
                       @Param("now") LocalDateTime now,
                       @Param("endReason") String endReason,
                       @Param("endBy") Long endBy,
                       @Param("endNote") String endNote);

    List<Long> queryDistinctActiveUserIds(@Param("asOf") LocalDateTime asOf);

    long countByScopeCode(@Param("scopeCode") Long scopeCode);
}
