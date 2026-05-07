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

package io.github.zzih.rudder.dao.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Runtime provider 审计标签。与 {@code RuntimeConfigService.activeProvider()} 对齐，
 * 记录每条 TaskInstance 跑在哪个 provider 上。
 */
@Getter
@RequiredArgsConstructor
public enum RuntimeType {

    LOCAL("local", "自建集群"),
    ALIYUN("aliyun", "Alibaba Cloud"),
    AWS("aws", "Amazon Web Services");

    /** provider 标识（小写），对应 RuntimePluginManager 的 provider key */
    private final String value;

    /** 前端显示名 */
    private final String label;

    public static RuntimeType fromValue(String value) {
        if (value == null) {
            return LOCAL;
        }
        for (RuntimeType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return LOCAL;
    }
}
