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

package io.github.zzih.rudder.service.workflow.stage;

import io.github.zzih.rudder.common.enums.approval.ApprovalLevel;
import io.github.zzih.rudder.common.enums.approval.ApprovalResourceType;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 数据权限申请的审批链。Ranger policy 是平台级资源(跨 workspace 生效),
 * 统一由 {@link ApprovalLevel#SUPER_ADMIN} 单级审批。
 */
@Component
class DataPermApplyStageFlow implements ApprovalStageFlow {

    @Override
    public String resourceType() {
        return ApprovalResourceType.DATA_PERM_APPLY;
    }

    @Override
    public List<String> resolveStageChain(ApprovalRecord record) {
        return List.of(
                ApprovalLevel.WORKSPACE_OWNER.name(),
                ApprovalLevel.SUPER_ADMIN.name());
    }
}
