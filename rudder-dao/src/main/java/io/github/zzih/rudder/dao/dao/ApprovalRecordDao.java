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

import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ApprovalRecordDao {

    IPage<ApprovalRecord> selectPage(int pageNum, int pageSize, String status,
                                     Long userId, int roleLevel, Long workspaceId);

    List<ApprovalRecord> selectByResourceTypeAndResourceCode(String resourceType, Long resourceCode);

    ApprovalRecord selectById(Long id);

    ApprovalRecord selectByExternalApprovalId(String externalApprovalId);

    int insert(ApprovalRecord record);

    int updateById(ApprovalRecord record);

    /** 推进到下一阶段（乐观锁，仅 status=PENDING 且 current_stage=oldStage 才生效）。 */
    int advanceStageIfPending(Long id, String oldStage, String newStage);

    /** 终结审批（乐观锁，仅 status=PENDING 才生效）。status 取 APPROVED / REJECTED / EXPIRED。 */
    int finalizeIfPending(Long id, String status);

    /** 申请人撤回（乐观锁，仅 status=PENDING 且 created_by=ownerId 才生效）。 */
    int withdrawIfPending(Long id, Long ownerId, String reason);

    /** 扫描超时单 id 列表（定时任务用）。 */
    List<Long> selectExpiredPendingIds(int limit);
}
