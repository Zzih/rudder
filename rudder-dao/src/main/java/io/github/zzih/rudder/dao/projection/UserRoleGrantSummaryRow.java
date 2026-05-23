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

/** "我的权限" 列表骨架行:同 roleId 多 grant 已 GROUP BY 合并,perm_count 来自 role_permission 子查询。 */
@Data
public class UserRoleGrantSummaryRow {

    private Long roleId;

    private String roleName;

    private Long grantId;

    private Long sourceApprovalId;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;

    private Long permCount;
}
