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

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_eval_run")
public class AiEvalRun extends BaseEntity {

    private String batchId;

    private Long caseId;

    /** AI provider(CLAUDE / OPENAI / ...),方便对比不同家的表现。 */
    private String provider;

    private String model;

    private Boolean passed;

    private BigDecimal score;

    /** LLM 最终回复全文(不含工具调用轨迹)。 */
    private String finalText;

    /**
     * 工具调用序列 JSON 数组,保留顺序:
     * <pre>
     *   [{"name":"list_tables","input":{...},"output":"...","success":true,"latencyMs":120}, ...]
     * </pre>
     */
    private String toolCallsJson;

    /** 失败原因列表 JSON 数组;passed=true 时为空数组。 */
    private String failReasonsJson;

    private Integer latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;
}
