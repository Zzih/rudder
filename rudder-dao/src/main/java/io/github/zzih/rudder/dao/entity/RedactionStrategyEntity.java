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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.entity.BaseEntity;
import io.github.zzih.rudder.common.enums.redaction.RedactionExecutorType;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** t_r_redaction_strategy 映射。脱敏策略 "怎么脱"。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_redaction_strategy")
public class RedactionStrategyEntity extends BaseEntity {

    /** 规则表引用用的稳定编码,跨环境一致。 */
    private String code;

    private String name;
    private String description;

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

    private Boolean enabled;
}
