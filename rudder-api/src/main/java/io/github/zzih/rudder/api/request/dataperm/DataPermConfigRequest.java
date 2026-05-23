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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 数据权限全局配置入参,SuperAdmin POST /api/config/data-perm。 */
@Data
public class DataPermConfigRequest {

    private boolean enabled;

    /** Ranger 鉴权 mode:开 → Reconciler 把 desired 推到 Ranger。 */
    private boolean rangerModeEnabled;

    /** Local 鉴权 mode:开 → Worker 跑 SQL 前查 snapshot 表本地鉴权。 */
    private boolean localModeEnabled;

    private String rangerAdminUrl;

    private String rangerAdminUsername;

    private String rangerAdminPassword;

    @Min(1_000)
    private int rangerAdminTimeoutMs = 10_000;

    @Min(1)
    private int rangerAdminPageSize = 1_000;

    @Min(1)
    private int rangerWriteConcurrency = 4;

    @Min(60)
    private int reconcileIntervalSeconds = 300;

    @Min(60)
    private int reconcileLockTtlSeconds = 600;

    @Min(1)
    private int reconcileBatchSize = 100;

    @Min(1)
    private int reconcileFailureAlertThreshold = 3;

    private boolean ensureRangerUser;

    /**
     * 平台登记的 Ranger Service 列表。null = 沿用 active 配置不变;非 null = 全量替换。
     * 元素 code 为空表示新增(后端分配),非空表示更新或保留。
     */
    @Valid
    private List<DataPermScopeRequest> scopes;
}
