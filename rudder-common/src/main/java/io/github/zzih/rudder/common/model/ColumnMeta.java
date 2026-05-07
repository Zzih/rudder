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

package io.github.zzih.rudder.common.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 脱敏运行时对列的描述。与数据源 metadata 解耦以便复用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMeta {

    /** Rudder 数据源名。元数据 tag 查询需要。Worker 结果文件场景可能为 null。 */
    private String datasourceName;

    private String database;
    private String table;

    /** 结果集里对外的列名(可能是 alias)。 */
    private String name;

    /**
     * 原始列名。当调用方能从 JDBC ResultSetMetaData / SQL parser 拿到原始列名时填。
     * null 表示无法追溯(如 Trino/Hive,或计算列)。
     */
    private String originalColumn;

    /** 原始表名(同上,配合 originalColumn 查元数据 tag)。 */
    private String originalTable;

    private String dataType;
    private String comment;

    /** 该列在元数据平台上挂的 tags。空/null 表示未打 tag,会 fallback 到 COLUMN 规则。 */
    private List<String> tags;
}
