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

package io.github.zzih.rudder.service.dataperm.service;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;

import java.util.ArrayList;
import java.util.List;

/** 作用域块库表行各层值的归一化 / JSON 编解码工具,role 与 direct 路径共用。 */
public final class DataPermStatementSupport {

    public static final String STAR = "*";

    private static final List<String> STAR_LEVEL = List.of(STAR);

    private DataPermStatementSupport() {
    }

    /** 去空白 / 去重;含 {@code "*"} 则整层折叠为 {@code ["*"]}(通配与具体值互斥)。 */
    public static List<String> normLevel(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> v = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return v.contains(STAR) ? List.of(STAR) : v;
    }

    public static String toJson(List<String> v) {
        return JsonUtils.toJson(v == null ? List.of() : v);
    }

    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> v = JsonUtils.toList(json, String.class);
        return v == null ? List.of() : v;
    }

    /** normLevel + toJson 组合,资源行各层入库前的归一化编码。 */
    public static String normJson(List<String> raw) {
        return toJson(normLevel(raw));
    }

    /**
     * 适用层为空 → 显式落 {@code ["*"]}(不适用层留空不动)。不变量在持久化边界建立:reconciler 物化把
     * "适用但空"按 {@code "*"} 展开,若存储留空则 My Permissions 预览(按非空层拼路径)会窄于实际授权;
     * 规范化后两侧读到同一 3 态形(具体 / {@code ["*"]} / 不适用空),无需各自感知 plugin 层级即一致。
     * 入参四层须已 normLevel;返回固定序 {@code [catalog, database, table, column]}。
     */
    public static List<List<String>> canonicalizeForcedAll(List<ResourceLevel> hierarchy,
                                                           List<String> catalog, List<String> database,
                                                           List<String> table, List<String> column) {
        boolean[] applicable = {
                hierarchy.contains(ResourceLevel.CATALOG),
                hierarchy.contains(ResourceLevel.DATABASE) || hierarchy.contains(ResourceLevel.SCHEMA),
                hierarchy.contains(ResourceLevel.TABLE),
                hierarchy.contains(ResourceLevel.COLUMN),
        };
        List<List<String>> levels = new ArrayList<>(List.of(catalog, database, table, column));
        for (int i = 0; i < levels.size(); i++) {
            if (applicable[i] && levels.get(i).isEmpty()) {
                levels.set(i, STAR_LEVEL);
            }
        }
        return levels;
    }

    public static String toGroupIdsJson(List<Long> groupIds) {
        return JsonUtils.toJson(groupIds == null ? List.of() : groupIds);
    }

    public static List<Long> parseGroupIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Long> v = JsonUtils.toList(json, Long.class);
        return v == null ? List.of() : v;
    }

    /**
     * 各层取值的笛卡尔积。每层列表由调用方按层级语义构造(null 占位 / {@code "*"} / 具体值),
     * 本方法只负责组合;任一层空列表 → 空结果。返回 {@code [catalog, database, table, column]} 四元组。
     */
    public static List<String[]> cartesian(List<String> catalogs, List<String> databases,
                                           List<String> tables, List<String> columns) {
        List<String[]> out = new ArrayList<>();
        for (String c : catalogs) {
            for (String d : databases) {
                for (String t : tables) {
                    for (String col : columns) {
                        out.add(new String[]{c, d, t, col});
                    }
                }
            }
        }
        return out;
    }
}
