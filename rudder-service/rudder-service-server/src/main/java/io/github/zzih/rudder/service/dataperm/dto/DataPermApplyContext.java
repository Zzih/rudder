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

package io.github.zzih.rudder.service.dataperm.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据权限申请的结构化提交内容 —— 序列化后存进
 * {@code t_r_approval_record.ext_data},审批通过时
 * {@code DataPermApprovalIntegration.onFinalized} 反查解析。
 *
 * <p>record 保证不可变,Jackson 自动序列化。
 */
public record DataPermApplyContext(
        Long applicantUserId,
        Long applicantWorkspaceId,
        List<Long> bundleIds,
        List<DataPermStatementDTO> directGrants,
        LocalDateTime expireAt) {
}
