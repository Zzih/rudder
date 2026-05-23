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

import lombok.Data;

/**
 * 数据权限平台标量配置 DTO,字段镜像 {@code DataPermPlatformConfig} entity。
 * Scope 列表是独立子领域,不挂在此 DTO,通过 scope dao / service 单独查。
 */
@Data
public class DataPermConfigDTO {

    private Boolean enabled;
    private Boolean rangerModeEnabled;
    private Boolean localModeEnabled;
    private String rangerAdminUrl;
    private String rangerAdminUsername;
    private String rangerAdminPassword;
    private Integer rangerAdminTimeoutMs;
    private Integer rangerAdminPageSize;
    private Integer rangerWriteConcurrency;
    private Integer reconcileIntervalSeconds;
    private Integer reconcileLockTtlSeconds;
    private Integer reconcileBatchSize;
    private Integer reconcileFailureAlertThreshold;
    private Boolean ensureRangerUser;
}
