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

package io.github.zzih.rudder.service.dataperm.client.ranger;

import java.util.List;
import java.util.Optional;

/**
 * Ranger Admin REST 客户端 —— Rudder 写 policy / 拉 policy 列表的唯一入口。
 *
 * <p>实现层职责:Basic Auth、JSON 序列化、分页拉取、错误码归一为
 * {@link io.github.zzih.rudder.common.exception.BizException}。
 * 不含 retry / 并发写,这些是上层调度的职责。
 */
public interface RangerAdminRestClient {

    /** 探活;Admin 不可达抛 {@code RANGER_UNREACHABLE}。 */
    void healthCheck();

    /** Ranger Admin 中是否存在指定 service。404 视为不存在,其他错误抛 BizException。 */
    boolean serviceExists(String serviceName);

    /** 创建 policy。失败抛 BizException。 */
    RangerPolicy createPolicy(RangerPolicy policy);

    /** 按 Ranger id 更新 policy。失败抛 BizException。 */
    RangerPolicy updatePolicy(Long policyId, RangerPolicy policy);

    /** 按 Ranger id 删除 policy。404 视为已删,静默成功;其他错误抛 BizException。 */
    void deletePolicy(Long policyId);

    /** 按 (service, name) 查 policy;未命中返回 {@link Optional#empty()}。 */
    Optional<RangerPolicy> findPolicy(String serviceName, String policyName);

    /** 列指定 service 下**所有** policy(自动分页拉全量,不需调用方关心 startIndex/pageSize)。 */
    List<RangerPolicy> listPoliciesInService(String serviceName);

    /**
     * Ensure user 存在于 Ranger Admin user table。
     * 新版 Ranger(2.x+) 不强校验,默认**不调**;
     * 旧版严格,通过 {@code DataPermConfig.ensureRangerUser} flag 启用。
     * 调用幂等,已存在视为成功。
     */
    void ensureUser(String username);

    /**
     * 按 name 拉 service-def(access types + resources hierarchy 等)。
     * 404 抛 BizException(RANGER_SERVICE_DEF_NOT_FOUND);其他错误透传 BizException。
     */
    RangerServiceDef getServiceDef(String serviceDefName);
}
