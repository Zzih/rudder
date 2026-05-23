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

package io.github.zzih.rudder.service.dataperm.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资源层级。{@link #rangerKey} 在对接 Ranger 时即 policy resource map 的 key,
 * 大小写需与 Ranger service-def 完全一致;Local 鉴权也用该枚举识别 snapshot resource 维度。
 */
public enum ResourceLevel {

    CATALOG("catalog"),
    DATABASE("database"),
    SCHEMA("schema"),
    TABLE("table"),
    COLUMN("column");

    private static final Map<String, ResourceLevel> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ResourceLevel::rangerKey, Function.identity()));

    private final String rangerKey;

    ResourceLevel(String rangerKey) {
        this.rangerKey = rangerKey;
    }

    public String rangerKey() {
        return rangerKey;
    }

    /** 按 Ranger 端 resource name 反查 enum。Rudder 不支持的资源类型(udf / view / function 等)返空。 */
    public static Optional<ResourceLevel> fromKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }
}
