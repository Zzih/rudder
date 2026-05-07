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

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * t_r_ai_message 映射。所有对话消息 + 工具调用/结果都落在这一张表。
 * 追加式,唯一允许的更新是 assistant 消息在流式生成过程中累加 content 和最终状态。
 */
@Data
@TableName("t_r_ai_message")
public class AiMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** ULID,一轮对话的所有消息共享。 */
    private String turnId;

    /** user | assistant | tool_call | tool_result | system。 */
    private String role;

    /** PENDING | STREAMING | DONE | CANCELLED | FAILED;仅 assistant 有意义。 */
    private String status;

    @TableField("`content`")
    private String content;

    private String errorMessage;

    // ========== Tool 相关 ==========

    private String toolCallId;

    private String toolName;

    /** NATIVE | SKILL | MCP。 */
    private String toolSource;

    /** JSON。 */
    private String toolInput;

    private String toolOutput;

    private Boolean toolSuccess;

    private Integer toolLatencyMs;

    // ========== Token / Cost ==========

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer costCents;

    private Integer latencyMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
