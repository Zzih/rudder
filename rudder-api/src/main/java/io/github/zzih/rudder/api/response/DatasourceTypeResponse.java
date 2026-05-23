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

package io.github.zzih.rudder.api.response;

import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源类型元数据。前端 datasource 编辑表单据此动态渲染字段:
 * <ul>
 *   <li>{@code type} — 类型标识(MYSQL/HIVE/...)</li>
 *   <li>{@code params} — plugin 自报的可调字段(name/label/type/required/placeholder/defaultValue),
 *       前端按此循环渲染 {@code <input>} / {@code <switch>} / {@code <select>}</li>
 *   <li>{@code hasCatalog} — 三层引擎(StarRocks/Trino)= true,前端可据此显示 catalog 输入框</li>
 *   <li>{@code validationQuery} — 该 DB 的探活 SQL,展示用</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceTypeResponse {

    private String type;
    private boolean hasCatalog;
    private String validationQuery;
    private List<PluginParamDefinition> params;
}
