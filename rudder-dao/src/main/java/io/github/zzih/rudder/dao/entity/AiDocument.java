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

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_document")
public class AiDocument extends BaseEntity {

    /**
     * JSON 数组字符串(如 {@code [1,3]});null 表示所有工作区可见(平台级)。
     * <p>
     * 语义(跟 {@code t_r_ai_tool_config.workspaceIds} 同模型):
     * <ul>
     *   <li>{@code null} → 所有工作区都能检索到(平台共享 / SCHEMA 类强制此值)</li>
     *   <li>{@code [1,3]} → 仅 workspace 1 / 3 的 AI 能检索到</li>
     * </ul>
     */
    private String workspaceIds;
    /** WIKI|SCRIPT|SCHEMA|METRIC_DEF|RUNBOOK */
    private String docType;
    /** SCHEMA 类必填:HIVE|STARROCKS|TRINO|MYSQL|CLICKHOUSE|POSTGRES|... 用于跨引擎可见性过滤 */
    private String engineType;
    /** 增量同步唯一标识(如 metadata:{ds}:{db}.{table});手写 doc 为 null */
    private String sourceRef;
    private String title;
    private String content;
    /** 人读备注(更新原因 / 负责人 / 来源链接等)。不参与 RAG / embedding。 */
    private String description;
    private String contentHash;
    private LocalDateTime indexedAt;
}
