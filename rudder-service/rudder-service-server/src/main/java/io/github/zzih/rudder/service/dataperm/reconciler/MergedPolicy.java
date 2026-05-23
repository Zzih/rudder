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

import io.github.zzih.rudder.service.dataperm.config.PluginType;

import java.util.List;
import java.util.Set;

/**
 * 跨 user 聚合后的 Ranger policy 目标态。
 *
 * <p>Ranger 的资源唯一性是 (service, resource pattern) 二元组,同一 pattern 只能有一条 policy。
 * 同 policy 内通过**多个 policyItem** 实现不同 user 各自不同的 access 集:不同 access 集的 user
 * 分到不同 {@link Bucket},避免 access 取并集导致权限提升。
 *
 * <p>{@link #userIds} 是 caller 的 user id 集合,用于 Change 失败时反推哪些 user 受影响。
 */
public record MergedPolicy(
        Long scopeCode,
        String rangerServiceName,
        PluginType pluginType,
        String policyName,
        List<String> resourcePath,
        List<Bucket> buckets,
        Set<Long> userIds) {

    /** 一组 access 集相同的 user 桶,直接映射 Ranger {@code policyItem}。 */
    public record Bucket(List<String> users, List<String> accesses) {
    }
}
