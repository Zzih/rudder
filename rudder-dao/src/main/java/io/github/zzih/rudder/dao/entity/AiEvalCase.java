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

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_eval_case")
public class AiEvalCase extends BaseEntity {

    /** SQL_GEN|OPTIMIZE|DEBUG|EXPLAIN|... */
    private String category;

    private String taskType;

    /** EASY|MEDIUM|HARD */
    private String difficulty;

    /** 执行模式:AGENT(带工具) / CHAT(纯对话)。默认 AGENT,最能模拟真实场景。 */
    private String mode;

    /** 可选:在哪个数据源语境下跑 — 决定 system prompt 注入的 schema / dialect。 */
    private Long datasourceId;

    /** 冗余记录,ds 被删后仍能知道 case 原本跑在什么引擎上。 */
    private String engineType;

    /** 归属工作空间,null = 平台级共享。 */
    private Long workspaceId;

    private String prompt;

    /**
     * 额外上下文 (JSON 对象):可选字段
     * <ul>
     *   <li>{@code selection}: String, 模拟编辑器选中文本</li>
     *   <li>{@code pinnedTables}: String[], 模拟置顶表</li>
     *   <li>{@code scriptCode}: Long, 模拟当前脚本</li>
     * </ul>
     */
    private String contextJson;

    /** 断言 spec:文本/工具调用/性能 三组。格式见 {@code ExpectedSpec}。 */
    private String expectedJson;

    private Boolean active;

    private Long createdBy;
}
