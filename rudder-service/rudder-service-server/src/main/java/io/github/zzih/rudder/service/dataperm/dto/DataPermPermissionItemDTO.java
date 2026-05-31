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

package io.github.zzih.rudder.service.dataperm.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作用域块拍平后的单元组展示项,跨 controller / service 边界(只读出口)。
 *
 * <p>资源定位 4 元组。catalog/db/table/column 任一可为 NULL("此层不适用此 plugin")
 * 或 {@code "*"}("该层级全部")。
 *
 * <p>权限表示分两类来源:当前配置(作用域块)出口填 {@code groupNames}(展示名);
 * 历史快照(snapshot 仅存展开后的裸 access)回放时填 {@code accesses}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPermPermissionItemDTO {

    /** 引用 {@code DataPermConfig.scopes[].code}。 */
    private Long scopeCode;

    /** 从 {@code DataPermConfig.scopes} 反查的展示名,grant view 出口处填充。 */
    private String scopeName;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** 操作分组展示名,当前配置出口处回填(供前端直接渲染)。 */
    private List<String> groupNames;

    /** plugin 原生 access 字符串列表;仅历史快照回放时填,当前配置路径为 null。 */
    private List<String> accesses;

    /** 仅 direct grant 行视图填:该条 direct grant 的生效/到期。role 视图为 null(role 级时间在 grant card 头)。 */
    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;
}
