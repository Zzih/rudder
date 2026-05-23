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

package io.github.zzih.rudder.service.dataperm.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerAdminRestClient;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyResource;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerServiceDef;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermRolePermissionItemDTO;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/** 3 个 plugin adapter (HADOOP_SQL / TRINO / STARROCKS) 的资源映射 + access 闭集校验。 */
class RangerResourceAdapterTest {

    // ---------- 构造工厂 ----------

    private static RangerServiceDef hiveDef() {
        return RangerServiceDef.builder()
                .name("hive")
                .accessTypes(List.of(
                        accessType("select"), accessType("update"), accessType("create"),
                        accessType("drop"), accessType("alter"), accessType("all")))
                .resources(List.of(
                        resource("database", 10, null),
                        resource("table", 20, "database"),
                        resource("column", 30, "table")))
                .build();
    }

    private static RangerServiceDef trinoDef() {
        return RangerServiceDef.builder()
                .name("trino")
                .accessTypes(List.of(
                        accessType("select"), accessType("insert"), accessType("create"),
                        accessType("drop"), accessType("alter"), accessType("all")))
                .resources(List.of(
                        resource("catalog", 10, null),
                        resource("schema", 20, "catalog"),
                        resource("table", 30, "schema"),
                        resource("column", 40, "table")))
                .build();
    }

    private static RangerServiceDef starrocksDef() {
        return RangerServiceDef.builder()
                .name("starrocks")
                .accessTypes(List.of(
                        accessType("select"), accessType("insert"), accessType("update"),
                        accessType("delete"), accessType("drop"), accessType("alter")))
                .resources(List.of(
                        resource("catalog", 10, null),
                        resource("database", 20, "catalog"),
                        resource("table", 30, "database"),
                        resource("column", 40, "table")))
                .build();
    }

    private static RangerServiceDef.AccessType accessType(String name) {
        return RangerServiceDef.AccessType.builder().name(name).build();
    }

    private static RangerServiceDef.Resource resource(String name, int level, String parent) {
        return RangerServiceDef.Resource.builder().name(name).level(level).parent(parent).build();
    }

    /** mock GlobalCacheService 把 (RANGER_SERVICE_DEFS, type) → ServiceDef 直接返回,跳过真 cache 行为。 */
    private static GlobalCacheService cacheReturning(String serviceType, RangerServiceDef def) {
        GlobalCacheService cache = mock(GlobalCacheService.class);
        when(cache.getOrLoad(eq(GlobalCacheKey.RANGER_SERVICE_DEFS), eq(serviceType),
                eq(RangerServiceDef.class), any(Supplier.class)))
                .thenReturn(def);
        return cache;
    }

    /** 测试统一假定 rangerModeEnabled = true,从而走 Ranger service-def 路径(而非 base spec)。 */
    private static DataPermConfigService rangerEnabledConfig() {
        DataPermConfigService configService = mock(DataPermConfigService.class);
        DataPermConfigDTO dto = new DataPermConfigDTO();
        dto.setEnabled(true);
        dto.setRangerModeEnabled(true);
        when(configService.active()).thenReturn(dto);
        return configService;
    }

    private static HadoopSqlRangerAdapter hadoopSql() {
        return new HadoopSqlRangerAdapter(
                cacheReturning("hive", hiveDef()),
                mock(RangerAdminRestClient.class),
                rangerEnabledConfig());
    }

    private static TrinoRangerResourceAdapter trino() {
        return new TrinoRangerResourceAdapter(
                cacheReturning("trino", trinoDef()),
                mock(RangerAdminRestClient.class),
                rangerEnabledConfig());
    }

    private static StarRocksRangerResourceAdapter starrocks() {
        return new StarRocksRangerResourceAdapter(
                cacheReturning("starrocks", starrocksDef()),
                mock(RangerAdminRestClient.class),
                rangerEnabledConfig());
    }

    // ---------- HADOOP_SQL (Hive / Spark Thrift / Impala 共用) ----------

    @Test
    void hadoopSql_explicitFields_writesDatabaseTableColumn() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .databaseName("ods").tableName("orders").columnName("email")
                .accesses(List.of("select")).build();

        Map<String, RangerPolicyResource> resources = hadoopSql().toRangerResource(item);

        assertThat(resources).containsOnlyKeys("database", "table", "column");
        assertThat(resources.get("database").getValues()).containsExactly("ods");
        assertThat(resources.get("table").getValues()).containsExactly("orders");
        assertThat(resources.get("column").getValues()).containsExactly("email");
        assertThat(resources.get("database").getIsExcludes()).isFalse();
        assertThat(resources.get("database").getIsRecursive()).isFalse();
    }

    @Test
    void hadoopSql_nullColumn_writesStarAtColumn() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .databaseName("ods").tableName("orders").columnName(null)
                .accesses(List.of("select")).build();

        Map<String, RangerPolicyResource> resources = hadoopSql().toRangerResource(item);
        assertThat(resources.get("column").getValues()).containsExactly("*");
    }

    @Test
    void hadoopSql_catalogIgnored() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .catalogName("hive_catalog_should_be_ignored")
                .databaseName("ods").tableName("orders").columnName("*")
                .build();

        Map<String, RangerPolicyResource> resources = hadoopSql().toRangerResource(item);
        assertThat(resources).doesNotContainKey("catalog");
        assertThat(resources).doesNotContainKey("schema");
    }

    @Test
    void hadoopSql_invalidAccess_fails() {
        assertThatThrownBy(() -> hadoopSql().validateAccesses(List.of("select", "nuke")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.APPLICATION_INVALID);
    }

    @Test
    void hadoopSql_emptyAccess_fails() {
        assertThatThrownBy(() -> hadoopSql().validateAccesses(List.of()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void hadoopSql_validAccess_passes() {
        hadoopSql().validateAccesses(List.of("select", "update"));
    }

    // ---------- TRINO ----------

    @Test
    void trino_writesCatalogSchemaTableColumn() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .catalogName("wh").databaseName("public").tableName("t1").columnName("*")
                .build();

        Map<String, RangerPolicyResource> resources = trino().toRangerResource(item);
        assertThat(resources).containsOnlyKeys("catalog", "schema", "table", "column");
        assertThat(resources.get("catalog").getValues()).containsExactly("wh");
        assertThat(resources.get("schema").getValues()).containsExactly("public");
        assertThat(resources.get("table").getValues()).containsExactly("t1");
        assertThat(resources.get("column").getValues()).containsExactly("*");
    }

    @Test
    void trino_nullCatalog_writesStar() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .databaseName("public").tableName("t1").columnName("c").build();

        Map<String, RangerPolicyResource> resources = trino().toRangerResource(item);
        assertThat(resources.get("catalog").getValues()).containsExactly("*");
    }

    @Test
    void trino_invalidAccessFails() {
        // execute 不在我们 stub 的 trinoDef 闭集里,应被拒
        assertThatThrownBy(() -> trino().validateAccesses(List.of("select", "execute")))
                .isInstanceOf(BizException.class);
    }

    // ---------- STARROCKS ----------

    @Test
    void starrocks_writesCatalogDatabaseTableColumn() {
        DataPermRolePermissionItemDTO item = DataPermRolePermissionItemDTO.builder()
                .catalogName("default").databaseName("mart").tableName("*").columnName("*")
                .build();

        Map<String, RangerPolicyResource> resources = starrocks().toRangerResource(item);
        assertThat(resources).containsOnlyKeys("catalog", "database", "table", "column");
        assertThat(resources.get("catalog").getValues()).containsExactly("default");
        assertThat(resources.get("database").getValues()).containsExactly("mart");
        assertThat(resources.get("table").getValues()).containsExactly("*");
    }

    @Test
    void starrocks_validAccessTypes() {
        starrocks().validateAccesses(List.of("select", "insert", "drop"));
    }

    // ---------- 公共 ----------

    @Test
    void allAdaptersExposeNonEmptyAccessClosedSet() {
        for (RangerResourceAdapter a : List.of(hadoopSql(), trino(), starrocks())) {
            assertThat(a.supportedAccessTypes()).as(a.supportedPluginType().name()).isNotEmpty();
            assertThat(a.resourceHierarchy()).as(a.supportedPluginType().name()).isNotEmpty();
            assertThat(a.rangerServiceType()).as(a.supportedPluginType().name()).isNotBlank();
        }
    }
}
