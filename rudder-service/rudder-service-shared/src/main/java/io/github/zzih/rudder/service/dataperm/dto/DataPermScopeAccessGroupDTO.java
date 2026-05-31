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

import java.util.List;

import lombok.Data;

/**
 * 操作分组 DTO,作为 {@link DataPermScopeDTO#getAccessGroups()} 的嵌套项随权限域一起增删改。
 *
 * <p>{@code id} 为空表示新增(保存时分配);非空表示更新或保留。{@code accesses} 是 plugin 原生
 * access 字符串列表,仅管理员配置分组时可见,申请 / 权限包编辑只引用 {@code id} + 展示 {@code name}。
 */
@Data
public class DataPermScopeAccessGroupDTO {

    private Long id;

    private Long scopeCode;

    private String name;

    private List<String> accesses;

    private String description;
}
