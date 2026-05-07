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

package io.github.zzih.rudder.datasource.dto;

import io.github.zzih.rudder.common.model.ColumnMeta;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasourcePreviewDTO {

    /** 结果集列名(可能是 alias)。 */
    private List<String> columns;

    /** 结果行。 */
    private List<Map<String, Object>> rows;

    /**
     * 列元信息,包含 Calcite SQL AST 解析出的原始表/列追溯。
     * 由 DatasourceService 构造;{@code rows} 已经过 sink 内部字段脱敏,调用方直接消费即可。
     */
    private List<ColumnMeta> columnMetas;
}
