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
 * 给 UI 渲染"按 PluginType 配置权限项"用:暴露层级 + access 闭集。
 */
@Data
@Builder
public class DataPermAdapterResponse {

    /** PluginType enum 名(如 HADOOP_SQL / STARROCKS / TRINO)。 */
    private String pluginType;

    /** Ranger service-def type,如 "hive" / "starrocks" / "trino"。 */
    private String rangerServiceType;

    /** 该 plugin 资源层级名(小写,与 Ranger key 一致):catalog/schema/database/table/column 的子集。 */
    private List<String> resourceLevels;

    /** access 闭集,顺序即 UI 渲染顺序。 */
    private List<String> accessTypes;
}
