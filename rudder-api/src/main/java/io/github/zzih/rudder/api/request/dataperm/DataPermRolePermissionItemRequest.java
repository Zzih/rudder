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

package io.github.zzih.rudder.api.request.dataperm;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 资源包内一条权限项的 HTTP 入参。 */
@Data
public class DataPermRolePermissionItemRequest {

    /** 引用 {@code DataPermConfig.scopes[].code}。 */
    @NotNull
    private Long scopeCode;

    /** NULL = 该 plugin 不适用此层; "*" = 全部。 */
    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** plugin 原生 access 列表,如 ["select","insert"]。必填,至少 1 个。 */
    @NotEmpty
    private List<String> accesses;
}
