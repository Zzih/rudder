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

import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;

import java.util.List;

/**
 * Ranger policy 命名工具。
 *
 * <p>规则:{@code rudder-{path}};{@code path} = hierarchy 各层值用 {@code "/"} 连接,
 * {@code "*"} / null 替成 {@code _ALL_}。
 *
 * <p>**为什么 name 不含 user**:Ranger 按 (service, resource pattern) 唯一,一个 resource 只能有一条 policy,
 * 多个 user 通过 policy 的 {@code users[]} 列表(或多个 policyItem)合并。Reconciler 在写 Ranger 前会
 * 按 (service, resource) 跨 user 聚合。
 *
 * <p>**为什么不 normalize username**:policyItem.users 字段跟 Ranger 端 XUser 名一一对应,Ranger 端 user 来源
 * 可能是 LDAP / OIDC 同步的 email 格式(如 {@code alice@corp.com}),含 {@code @.} 等字符。Rudder 端任何 normalize
 * 都会让 policyItem.users 引用到一个 Ranger 端不存在的 name。直接传原始 username 即可。
 */
public final class PolicyNaming {

    private static final String PREFIX = "rudder-";
    private static final String STAR = "_ALL_";

    private PolicyNaming() {
    }

    /** 构造 policy 名。values 按 hierarchy 顺序;null / blank / "*" → "_ALL_"。 */
    public static String build(List<ResourceLevel> hierarchy, List<String> values) {
        if (hierarchy.size() != values.size()) {
            throw new IllegalArgumentException(
                    "hierarchy/values size mismatch: hierarchy=" + hierarchy.size()
                            + ", values=" + values.size());
        }
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                path.append('/');
            }
            String raw = values.get(i);
            path.append(raw == null || raw.isBlank() || "*".equals(raw) ? STAR : raw);
        }
        return PREFIX + path;
    }

    public static boolean isRudderPolicy(String policyName) {
        return policyName != null && policyName.startsWith(PREFIX);
    }
}
