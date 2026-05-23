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

package io.github.zzih.rudder.service.dataperm.reconciler;

/**
 * Reconciler 单轮统计,跑完一轮后由 {@link DataPermReconciler#evaluateRound} 用于决定告警 / 计数。
 */
public record ReconcileStats(
        int totalUsers,
        int successUsers,
        int failedUsers,
        int createdPolicies,
        int updatedPolicies,
        int deletedPolicies,
        boolean rangerAdminReachable,
        String lastError) {

    public boolean isFullFailure() {
        return !rangerAdminReachable || (totalUsers > 0 && successUsers == 0);
    }

    public boolean isPartialFailure() {
        return rangerAdminReachable && failedUsers > 0 && successUsers > 0;
    }
}
