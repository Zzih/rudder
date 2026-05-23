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

/** 用户权限的"按来源"视图。一张卡片 = 一个 source: 一个 role grant 或一组同 approval 的 direct grants 合并。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGrantViewDTO {

    public enum Kind {
        ROLE, DIRECT
    }

    private Kind kind;

    /** ROLE 时填,关联 t_r_data_perm_role.id;DIRECT 时为 null。 */
    private Long roleId;

    /** ROLE 时填 role.name;DIRECT 时为 "Direct 授权"。 */
    private String roleName;

    private Long sourceApprovalId;

    private Long grantId;

    private LocalDateTime effectiveTime;

    /** {@code null} = 永久。 */
    private LocalDateTime expirationTime;

    /** 失效原因,active grant 为 null。 */
    private String endReason;

    /** ROLE 类型:role 当前的全部 permission 项;DIRECT 类型:该 grant 本身一条 item。 */
    private List<DataPermRolePermissionItemDTO> permissions;
}
