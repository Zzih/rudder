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

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 数据权限全局配置出参。
 *
 * <p>{@code rangerAdminPassword} 在 GET 时**不回填**(避免泄露),由前端保留占位串提示"未变更"。
 */
@Data
@Builder
public class DataPermConfigResponse {

    private boolean enabled;
    private boolean rangerModeEnabled;
    private boolean localModeEnabled;
    private String rangerAdminUrl;
    private String rangerAdminUsername;
    private boolean passwordConfigured;
    private int rangerAdminTimeoutMs;
    private int rangerAdminPageSize;
    private int rangerWriteConcurrency;
    private int reconcileIntervalSeconds;
    private int reconcileLockTtlSeconds;
    private int reconcileBatchSize;
    private int reconcileFailureAlertThreshold;
    private boolean ensureRangerUser;

    /** 平台登记的 Ranger Service 列表。 */
    private List<DataPermScopeResponse> scopes;
}
