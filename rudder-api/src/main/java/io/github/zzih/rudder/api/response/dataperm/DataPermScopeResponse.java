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

/** 数据权限域 (Scope) 出参,嵌入 {@link DataPermConfigResponse#scopes}。 */
@Data
@Builder
public class DataPermScopeResponse {

    private Long code;
    private String name;
    /** PluginType 枚举名。 */
    private String pluginType;
    private Long metadataDatasourceId;
    /** 受 Local 鉴权管控的任务类型列表 (TaskType 枚举名)。 */
    private List<String> managedTaskTypes;
    /** Ranger 端对应的 service.name;Ranger mode 关时可为空。 */
    private String rangerServiceName;
    private String description;
    private boolean enabled;

    /** 该域下的操作分组(含 accesses);仅管理员配置端点回填,精简列表为 null。 */
    private List<DataPermScopeAccessGroupResponse> accessGroups;
}
