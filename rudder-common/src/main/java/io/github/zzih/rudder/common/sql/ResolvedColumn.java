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

package io.github.zzih.rudder.common.sql;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个结果列 → 原始列的解析结果。
 * <ul>
 *   <li>{@code resultName} 结果集里的列名(alias 后的)</li>
 *   <li>{@code originalTable}/{@code originalColumn} 追溯到的原始表/列,简单列引用时填</li>
 *   <li>{@code derivedFrom} 计算列/函数/聚合 的组成,空集则是"完全派生(常量)"</li>
 *   <li>{@code aggregate} 是否是聚合结果(SUM/COUNT/...)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedColumn {

    private String resultName;
    private String originalTable;
    private String originalColumn;

    /** 计算列 / 表达式 / 聚合 等 —— 收集表达式里涉及到的所有原始列引用。 */
    @Builder.Default
    private List<SourceRef> derivedFrom = new ArrayList<>();

    private boolean aggregate;

    /** 原始列引用三元组(database 可空)。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRef {

        private String database;
        private String table;
        private String column;
    }

    /** 简单列引用(非计算、非聚合)才有明确的原始 table+column。 */
    public boolean isSimpleRef() {
        return !aggregate && originalColumn != null && derivedFrom.isEmpty();
    }

    public static ResolvedColumn simple(String resultName, String table, String column) {
        return ResolvedColumn.builder()
                .resultName(resultName)
                .originalTable(table)
                .originalColumn(column)
                .build();
    }

    public static ResolvedColumn derived(String resultName, List<SourceRef> sources, boolean aggregate) {
        return ResolvedColumn.builder()
                .resultName(resultName)
                .derivedFrom(sources == null ? new ArrayList<>() : new ArrayList<>(sources))
                .aggregate(aggregate)
                .build();
    }
}
