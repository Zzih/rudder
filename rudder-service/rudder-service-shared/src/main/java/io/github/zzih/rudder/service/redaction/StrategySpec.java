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

package io.github.zzih.rudder.service.redaction;

import io.github.zzih.rudder.common.enums.redaction.RedactionExecutorType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 策略的纯数据描述,用于 admin UI 试跑。避免 rudder-redaction-api 依赖 rudder-dao 的实体类。
 * 字段与 {@code t_r_redaction_strategy} 表一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategySpec {

    private RedactionExecutorType executorType;

    // REGEX_REPLACE
    private String matchRegex;
    private String replacement;

    // PARTIAL
    private Integer keepPrefix;
    private Integer keepSuffix;
    private String maskChar;

    // REPLACE
    private String replaceValue;

    // HASH
    private Integer hashLength;
}
