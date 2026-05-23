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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 数据权限域 (Scope) 入参,嵌入 {@link DataPermConfigRequest#scopes}。code 为空 = 新增;非空 = 更新。 */
@Data
public class DataPermScopeRequest {

    private Long code;

    @NotBlank
    private String name;

    /** PluginType 枚举名 (HADOOP_SQL / TRINO / STARROCKS / ...)。 */
    @NotBlank
    private String pluginType;

    @NotNull
    private Long metadataDatasourceId;

    /** 受 Local 鉴权管控的任务类型列表 (TaskType 枚举名);跨 Scope 全局唯一。 */
    private List<String> managedTaskTypes;

    /** Ranger 端对应的 service.name;仅 Ranger mode 开时由 service 层校验为必填。 */
    private String rangerServiceName;

    private String description;

    private boolean enabled = true;
}
