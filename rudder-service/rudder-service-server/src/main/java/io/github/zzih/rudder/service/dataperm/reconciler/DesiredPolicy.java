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

/**
 * 单 user 视角的 desired state policy(经 adapter 翻译 + 命名规则计算后的目标态)。
 *
 * <p>用于 snapshot 写入(per-user)。Ranger 端写入会先按 {@link #policyName} 跨 user 聚合成
 * {@link MergedPolicy},参见 {@code DataPermReconciler.mergeAcrossUsers}。
 */
public record DesiredPolicy(
        Long userId,
        String username,
        Long scopeCode,
        String rangerServiceName,
        PluginType pluginType,
        String policyName,
        List<String> accesses,
        List<String> resourcePath,
        List<PermSource> sources) {
}
