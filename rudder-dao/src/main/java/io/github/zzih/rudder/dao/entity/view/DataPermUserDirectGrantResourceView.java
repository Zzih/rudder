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

package io.github.zzih.rudder.dao.entity.view;

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * direct 库表行扁平视图:resource + 所属 block(userId/scopeCode/groupIds/时间窗)+ JOIN scope 元数据。
 * reconciler 与"我的权限/总览"展示共用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPermUserDirectGrantResourceView extends DataPermUserDirectGrantResource {

    private Long userId;
    private Long sourceApprovalId;
    private Long scopeCode;
    private String groupIds;
    private LocalDateTime effectiveTime;
    private LocalDateTime expirationTime;
    private String endReason;

    private String scopeName;
    private String pluginType;
    private String rangerServiceName;
    private Boolean scopeEnabled;
}
