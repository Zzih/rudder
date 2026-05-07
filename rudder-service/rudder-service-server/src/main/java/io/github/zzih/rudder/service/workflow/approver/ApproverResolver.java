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

package io.github.zzih.rudder.service.workflow.approver;

import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;

/**
 * 候选审批人解析器：按当前阶段算出谁能批这单。运行时计算（不持久化），
 * 等待期间 owner / project 创建者变更可立即反映。
 */
public interface ApproverResolver {

    /** 关联的阶段名（即 t_r_approval_record.current_stage 取值）。 */
    String stage();

    /** 算候选人 user_id 列表（已去除申请人本人）。 */
    List<Long> resolveCandidateUserIds(ApprovalRecord record);
}
