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

import lombok.Data;

/**
 * 作用域块:一个 scope + 一组操作分组 + 多条库表行({@link ResourcePathDTO})。
 *
 * <p>申请 / 权限包编辑的录入单位,跨 controller / service / 审批 ext_data 边界。{@code id} 为空=新增;
 * {@code groupNames} 出口处回填;{@code effectiveTime/expirationTime/endReason/sourceApprovalId}
 * 仅 direct 授权块展示时填。
 */
@Data
public class DataPermStatementDTO {

    private Long id;

    private Long scopeCode;

    private String scopeName;

    private List<Long> groupIds;

    private List<String> groupNames;

    private List<ResourcePathDTO> resources;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;

    private String endReason;

    private Long sourceApprovalId;
}
