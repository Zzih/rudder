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

package io.github.zzih.rudder.api.response.dataperm;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class DataPermPermissionItemResponse {

    /** 引用 {@code DataPermConfig.scopes[].code}。 */
    private Long scopeCode;

    private String scopeName;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** 操作分组展示名(当前配置)。 */
    private List<String> groupNames;

    /** plugin 原生 access 列表;仅历史快照回放时填,当前配置路径为 null。 */
    private List<String> accesses;

    /** 仅 direct grant 行视图填:该条 grant 的生效/到期。其他视图为 null。 */
    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;
}
