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

package io.github.zzih.rudder.service.approval.integration;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;

/**
 * 业务接入审批的标准接口。各走审批的业务模块(MCP / Publish / DataPerm 等)注册一个实现:
 * <ul>
 *   <li>{@link #buildRequest} 提交侧:业务实体 ID → ApprovalRequest
 *       (title / content / applicant / extra),stage 链与候选人由 ApprovalService 内部解析</li>
 *   <li>{@link #onFinalized} 终态侧:审批进入终态后推进业务自己的状态机.
 *       <b>必须幂等</b> ——可能被 {@link ApprovalIntegrationDispatcher} retry</li>
 * </ul>
 *
 * <p>实现合规要求:
 * <ul>
 *   <li>幂等:同一 event 多次调用结果一致(retry / 重启 replay 时不产生重复副作用)</li>
 *   <li>合法性校验:落业务态前校验关联资源仍存在,失效时抛 {@code BizException} 走失败链路,
 *       不静默落库 ghost 行</li>
 *   <li>事务边界:业务侧入库动作用 {@code @Transactional} 包,中途失败整体回滚</li>
 * </ul>
 */
public interface ApprovalIntegration {

    /** 业务标识,与 {@link ApprovalFinalizedEvent#resourceType()} 对齐。 */
    String resourceType();

    /**
     * 业务实体 ID → 审批请求体。**仅供 {@code ApprovalSubmitFacade} 调用**。
     *
     * <p>**可选实现**:有的业务(如 MCP token 创建)需要在提交时持有运行时上下文(创建命令的字段),
     * 不便走 facade,直接调 {@code ApprovalService.submit(...)};这种业务可以不重写此方法。
     */
    default ApprovalRequest buildRequest(Long resourceCode) {
        throw new UnsupportedOperationException(
                "Integration '" + resourceType() + "' does not support facade submit; "
                        + "call ApprovalService.submit directly.");
    }

    /** 终态回调,业务方据此推进自身状态机。必须幂等。 */
    void onFinalized(ApprovalFinalizedEvent event);
}
