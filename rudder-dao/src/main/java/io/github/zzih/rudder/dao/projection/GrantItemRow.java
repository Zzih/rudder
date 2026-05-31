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

package io.github.zzih.rudder.dao.projection;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 授权库表行经 {@code JSON_TABLE} 在 SQL 里笛卡尔展开后的单元组(每行一条 (catalog,db,table,column))。
 * 用于「我的数据权限」/「数据权限总览」展开列表的原生分页;group_ids 仍为 JSON,名字在 service 侧解析。
 */
@Data
public class GrantItemRow {

    private Long scopeCode;

    private String scopeName;

    private String groupIds;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** 仅直接授权填:所属块的生效 / 到期。 */
    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;
}
