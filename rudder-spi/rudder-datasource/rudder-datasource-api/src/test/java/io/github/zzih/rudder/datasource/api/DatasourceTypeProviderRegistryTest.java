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

package io.github.zzih.rudder.datasource.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 静态 Registry 注册 / 查找 / 重复检测覆盖。每个用例 teardown 清空,避免污染其他用例。
 */
class DatasourceTypeProviderRegistryTest {

    @BeforeEach
    void setUp() {
        DatasourceTypeProviderRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        DatasourceTypeProviderRegistry.reset();
    }

    @Test
    @DisplayName("register + get 来回一致")
    void registerThenGetRoundtrip() {
        DatasourceTypeProvider<?> mysql = new FakeProvider(DatasourceType.MYSQL);
        DatasourceTypeProviderRegistry.register(mysql);

        assertThat(DatasourceTypeProviderRegistry.get(DatasourceType.MYSQL)).isSameAs(mysql);
        assertThat(DatasourceTypeProviderRegistry.isRegistered(DatasourceType.MYSQL)).isTrue();
        assertThat(DatasourceTypeProviderRegistry.isRegistered(DatasourceType.FLINK)).isFalse();
    }

    @Test
    @DisplayName("get(未注册的 type) 抛 IllegalStateException 并提示可用类型")
    void getThrowsWhenTypeMissing() {
        DatasourceTypeProviderRegistry.register(new FakeProvider(DatasourceType.MYSQL));

        assertThatThrownBy(() -> DatasourceTypeProviderRegistry.get(DatasourceType.FLINK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FLINK")
                .hasMessageContaining("MYSQL");
    }

    @Test
    @DisplayName("同 type 重复注册同一实例幂等,不同实例抛 IllegalStateException")
    void duplicateRegisterSameInstanceIsIdempotent() {
        DatasourceTypeProvider<?> mysql = new FakeProvider(DatasourceType.MYSQL);
        DatasourceTypeProviderRegistry.register(mysql);
        DatasourceTypeProviderRegistry.register(mysql); // 幂等

        assertThatThrownBy(() -> DatasourceTypeProviderRegistry.register(new FakeProvider(DatasourceType.MYSQL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("register(null) 抛 IllegalArgumentException")
    void registerNullRejected() {
        assertThatThrownBy(() -> DatasourceTypeProviderRegistry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("snapshot 只读,反映已注册状态")
    void snapshotReflectsRegistered() {
        DatasourceTypeProviderRegistry.register(new FakeProvider(DatasourceType.MYSQL));
        DatasourceTypeProviderRegistry.register(new FakeProvider(DatasourceType.HIVE));

        assertThat(DatasourceTypeProviderRegistry.snapshot())
                .containsOnlyKeys(DatasourceType.MYSQL, DatasourceType.HIVE);
    }

    /** 测试桩,只回 dbType + propertiesClass,其它方法不应被本测试触发。 */
    private record FakeProvider(DatasourceType dbType) implements DatasourceTypeProvider<Object> {

        @Override
        public DatasourceType dbType() {
            return dbType;
        }

        @Override
        public Class<Object> propertiesClass() {
            return Object.class;
        }

        @Override
        public List<PluginParamDefinition> params() {
            return List.of();
        }

        @Override
        public String getDriverClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String buildJdbcUrl(String host, int port, String database, String paramsJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getValidationQuery() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHasCatalog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlDialect dialect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean validateConnection(Connection conn) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyStreamingFetch(Statement stmt) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String buildPreviewSql(String database, String table, int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
