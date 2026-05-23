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

/** 一张"权限来源"卡片(ROLE 一张 / 每条 DIRECT grant 一张)。 */
@Data
public class UserGrantViewResponse {

    /** "ROLE" 或 "DIRECT". */
    private String kind;

    private Long roleId;

    private String roleName;

    private Long sourceApprovalId;

    private Long grantId;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;

    private String endReason;

    private List<DataPermRolePermissionItemResponse> permissions;
}
