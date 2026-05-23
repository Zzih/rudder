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

import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;

import lombok.Data;

/**
 * 数据权限域 DTO。entity 端 {@code pluginType} 为 String、{@code managedTaskTypes} 为 JSON 字符串;
 * DTO 端转回强类型 (enum / List),由 service 内部 entity ↔ DTO 转换层处理。
 */
@Data
public class DataPermScopeDTO {

    private Long code;
    private String name;
    private PluginType pluginType;
    private Long metadataDatasourceId;
    private List<TaskType> managedTaskTypes;
    private String rangerServiceName;
    private String description;
    private Boolean enabled;
}
