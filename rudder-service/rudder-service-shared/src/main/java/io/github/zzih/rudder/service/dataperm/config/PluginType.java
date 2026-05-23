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

package io.github.zzih.rudder.service.dataperm.config;

import java.util.List;

/**
 * 数据权限 plugin 类型。每个 plugin 在 Rudder 端有 base spec(支持的 access 闭集 + 资源层级);
 * 启用 Ranger mode 时,运行时会以 Ranger Admin 拉取的 service-def 覆盖 base。
 *
 * <p>{@code RangerResourceAdapter} 按这个枚举索引(而非 datasource type),从而:
 * <ul>
 *   <li>Hive / Spark Thrift / Impala 等共用 {@code HADOOP_SQL} adapter — 它们底层挂同一 Hive Ranger plugin</li>
 *   <li>StarRocks 有独立 servicedef(catalog 三层 + 一堆 top-level resource)— 不能合并 HADOOP_SQL</li>
 *   <li>Trino 独立 servicedef(catalog/schema/table/column)</li>
 * </ul>
 *
 * <p>{@link #baseAccessTypes} 是 Local-only 部署的权威闭集,顺序即 UI 渲染顺序;对接 Ranger 后会被
 * Ranger service-def 的 accessTypes 覆盖(并经 categoryRank 重排)。
 */
public enum PluginType {

    /** Hive / Spark Thrift / Impala 共用,资源 db/table/column。 */
    HADOOP_SQL("hive",
            List.of("select", "update", "create", "drop", "alter", "index", "lock", "all", "read", "write"),
            List.of(ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN)),

    /** StarRocks 原生 servicedef,资源 catalog/database/table/column + top-level user/system 等。 */
    STARROCKS("starrocks",
            List.of("select", "insert", "update", "delete", "create", "drop", "alter", "export", "refresh"),
            List.of(ResourceLevel.CATALOG, ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN)),

    /** Trino 原生 servicedef,资源 catalog/schema/table/column。 */
    TRINO("trino",
            List.of("select", "insert", "delete", "update", "create", "drop", "alter", "use", "execute", "all"),
            List.of(ResourceLevel.CATALOG, ResourceLevel.SCHEMA, ResourceLevel.TABLE, ResourceLevel.COLUMN)),

    // 以下为占位类型:Rudder 尚未实装对应 adapter / base spec。启用前须补全 baseAccessTypes 与 baseHierarchy,
    // 否则 Local-only 部署下 supportedAccessTypes 返空,UI 将无 access 可选。
    HBASE("hbase", List.of(), List.of()),
    HDFS("hdfs", List.of(), List.of()),
    KAFKA("kafka", List.of(), List.of());

    private final String serviceType;
    private final List<String> baseAccessTypes;
    private final List<ResourceLevel> baseHierarchy;

    PluginType(String serviceType, List<String> baseAccessTypes, List<ResourceLevel> baseHierarchy) {
        this.serviceType = serviceType;
        this.baseAccessTypes = List.copyOf(baseAccessTypes);
        this.baseHierarchy = List.copyOf(baseHierarchy);
    }

    /** Ranger service-def 的 service type 名(小写,如 "hive" / "trino" / "starrocks")。 */
    public String serviceType() {
        return serviceType;
    }

    public List<String> baseAccessTypes() {
        return baseAccessTypes;
    }

    public List<ResourceLevel> baseHierarchy() {
        return baseHierarchy;
    }

    /** null / 未知名返 null;调用方按 dao entity 端 String 列转 enum 用,允许 DB 端 schema drift 时降级。 */
    public static PluginType parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
