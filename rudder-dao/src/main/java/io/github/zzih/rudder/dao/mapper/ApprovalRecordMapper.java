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

import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    IPage<ApprovalRecord> queryPage(IPage<ApprovalRecord> page,
                                    @Param("status") String status,
                                    @Param("userId") Long userId,
                                    @Param("roleLevel") int roleLevel,
                                    @Param("workspaceId") Long workspaceId);

    List<ApprovalRecord> queryByResourceTypeAndResourceCode(@Param("resourceType") String resourceType,
                                                            @Param("resourceCode") Long resourceCode);

    ApprovalRecord queryByExternalApprovalId(@Param("externalApprovalId") String externalApprovalId);

    /**
     * 推进到下一阶段：仅当 status=PENDING 且 current_stage=oldStage 时更新成功。
     * 影响行数 0 = 已被先决议者抢先推进 / 已终结。
     */
    int advanceStageIfPending(@Param("id") Long id,
                              @Param("oldStage") String oldStage,
                              @Param("newStage") String newStage);

    /**
     * 终结审批（APPROVED / REJECTED / EXPIRED）：仅当 status=PENDING 时更新成功。
     */
    int finalizeIfPending(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("resolvedAt") LocalDateTime resolvedAt);

    /**
     * 撤回审批：仅当 status=PENDING 且 created_by=ownerId 时更新成功。
     */
    int withdrawIfPending(@Param("id") Long id,
                          @Param("ownerId") Long ownerId,
                          @Param("withdrawnAt") LocalDateTime withdrawnAt,
                          @Param("withdrawnReason") String withdrawnReason);

    /**
     * 扫描超时未决议单。返回 id 列表给定时任务批量 finalize。
     */
    List<Long> queryExpiredPendingIds(@Param("now") LocalDateTime now,
                                      @Param("limit") int limit);
}
