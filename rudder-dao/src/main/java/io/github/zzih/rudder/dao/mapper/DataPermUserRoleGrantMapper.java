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

import io.github.zzih.rudder.dao.entity.DataPermUserRoleGrant;
import io.github.zzih.rudder.dao.entity.view.DataPermUserRoleGrantDetailView;
import io.github.zzih.rudder.dao.projection.UserRoleGrantSummaryRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DataPermUserRoleGrantMapper extends BaseMapper<DataPermUserRoleGrant> {

    /** 查 user 在某时间点处于"有效"状态的所有 role grants;LEFT JOIN role 出 roleName。 */
    List<DataPermUserRoleGrantDetailView> queryActiveByUser(@Param("userId") Long userId,
                                                            @Param("asOf") LocalDateTime asOf);

    /** 批量版本,reconciler 一次拉全部 active user 的 role grants。 */
    List<DataPermUserRoleGrant> queryActiveByUserIds(@Param("userIds") java.util.Collection<Long> userIds,
                                                     @Param("asOf") LocalDateTime asOf);

    /** "我的权限" 列表骨架:按 roleId 聚合,LEFT JOIN role 名,子查询 perm_count。1 次 SQL 全出。 */
    List<UserRoleGrantSummaryRow> queryActiveSummaryByUser(@Param("userId") Long userId,
                                                           @Param("asOf") LocalDateTime asOf);

    /** 单 (user, role) 是否在 asOf 时活跃。idx_user_time 命中,最多 1 行。 */
    Integer existsActiveByUserAndRole(@Param("userId") Long userId,
                                      @Param("roleId") Long roleId,
                                      @Param("asOf") LocalDateTime asOf);

    /** 查 user 已失效(expiration_time <= now)的 role grants;LEFT JOIN role 出 roleName。 */
    List<DataPermUserRoleGrantDetailView> queryInactiveByUser(@Param("userId") Long userId,
                                                              @Param("now") LocalDateTime now);

    /** 批量统计:返回 [{roleId, cnt}] 行集合,cnt=COUNT(DISTINCT user_id) 在 asOf 时活跃,DAO 转 Map<roleId, count>。 */
    List<Map<String, Object>> queryActiveCountByRoleIds(@Param("roleIds") List<Long> roleIds,
                                                        @Param("asOf") LocalDateTime asOf);

    /** 查 source approval 关联的全部 grants(用于按 approval 撤销)。 */
    List<DataPermUserRoleGrant> queryByApprovalId(@Param("approvalId") Long approvalId);

    /** 失效本 grant 行(仅 expiration_time IS NULL 时生效,幂等)。 */
    int expireIfActive(@Param("id") Long id,
                       @Param("now") LocalDateTime now,
                       @Param("endReason") String endReason,
                       @Param("endBy") Long endBy,
                       @Param("endNote") String endNote);

    /** 按 role 级联失效全部活跃 grant(删包用)。 */
    int expireAllByRoleId(@Param("roleId") Long roleId,
                          @Param("now") LocalDateTime now,
                          @Param("endReason") String endReason);

    /** 查询用户当前所有活跃 grants 的去重 userId 列表(reconciler 扫所有用户用)。 */
    List<Long> queryDistinctActiveUserIds(@Param("asOf") LocalDateTime asOf);
}
