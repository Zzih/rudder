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

import io.github.zzih.rudder.dao.entity.ApprovalRecord;

import java.util.List;

/**
 * 审批阶段链解析器：按资源类型决定一个新审批单要走哪些阶段。
 * 提交时调一次锁定到 record.stage_chain。
 *
 * <p>新增审批类型只需新建一个实现 + 声明 {@link #resourceType()}，无需改其他代码。
 */
public interface ApprovalStageFlow {

    /** 关联的 t_r_approval_record.resource_type */
    String resourceType();

    /** 算出该审批单的整条阶段链。返回顺序即为流转顺序，最后一个阶段决议通过即整单通过。 */
    List<String> resolveStageChain(ApprovalRecord record);
}
