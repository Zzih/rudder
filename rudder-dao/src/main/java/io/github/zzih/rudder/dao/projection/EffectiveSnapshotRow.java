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

/** "全部用户权限" 扁平审计行:user effective snapshot LEFT JOIN t_r_user + t_r_data_perm_scope。 */
@Data
public class EffectiveSnapshotRow {

    private Long userId;

    private String username;

    private Long version;

    private LocalDateTime snapshotTime;

    private Long scopeCode;

    private String scopeName;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** JSON 数组字符串,如 {@code ["select","insert"]}。 */
    private String accesses;

    /** JSON 数组字符串,如 {@code [{"kind":"ROLE","id":7},{"kind":"DIRECT","id":42}]}。 */
    private String sourceKinds;
}
