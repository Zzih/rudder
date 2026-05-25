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

package io.github.zzih.rudder.datasource.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MysqlTypeProviderTest {

    @Test
    @DisplayName("type/dialect 与 MYSQL 一致")
    void typeAndDialect() {
        MysqlTypeProvider p = new MysqlTypeProvider();
        assertThat(p.dbType()).isEqualTo(DatasourceType.MYSQL);
        assertThat(p.dialect()).isEqualTo(SqlDialect.MYSQL);
    }

    @Test
    @DisplayName("applyStreamingFetch 设 Integer.MIN_VALUE")
    void streamingFetch() throws SQLException {
        Statement stmt = mock(Statement.class);
        new MysqlTypeProvider().applyStreamingFetch(stmt);
        verify(stmt).setFetchSize(Integer.MIN_VALUE);
    }

    @Test
    @DisplayName("buildJdbcUrl: plugin params 不再拼 URL(走 Properties single source),只保留 enum 模板内置 default")
    void buildJdbcUrlOnlyTemplateParams() {
        // 用户配置的 useSSL/allowPublicKeyRetrieval 由 task init / Hikari 走 Properties 路径传递,
        // 避免与 URL 重复(Trino driver 严格禁止双写)。URL 只保留 enum 模板自带的工程默认。
        String json = "{\"useSSL\":false,\"allowPublicKeyRetrieval\":true}";
        String url = new MysqlTypeProvider().buildJdbcUrl("h", 3306, "db", json);

        assertThat(url).isEqualTo(DatasourceType.MYSQL.buildJdbcUrl("h", 3306, "db"))
                .doesNotContain("useSSL=false")
                .doesNotContain("allowPublicKeyRetrieval=true");
    }

    @Test
    @DisplayName("buildJdbcUrl: null/空 params 走 enum 默认 URL")
    void buildJdbcUrlNullProps() {
        String url = new MysqlTypeProvider().buildJdbcUrl("h", 3306, "db", null);
        assertThat(url).isEqualTo(DatasourceType.MYSQL.buildJdbcUrl("h", 3306, "db"));
    }

    @Test
    @DisplayName("getValidationQuery 走基类 SELECT 1")
    void validationQuery() {
        assertThat(new MysqlTypeProvider().getValidationQuery()).isEqualTo("SELECT 1");
    }
}
