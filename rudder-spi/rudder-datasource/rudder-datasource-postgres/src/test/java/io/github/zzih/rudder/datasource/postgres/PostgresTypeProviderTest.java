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

package io.github.zzih.rudder.datasource.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostgresTypeProviderTest {

    @Test
    @DisplayName("type/dialect 与 POSTGRES 一致")
    void typeAndDialect() {
        PostgresTypeProvider p = new PostgresTypeProvider();
        assertThat(p.dbType()).isEqualTo(DatasourceType.POSTGRES);
        assertThat(p.dialect()).isEqualTo(SqlDialect.POSTGRES);
    }

    @Test
    @DisplayName("applyStreamingFetch 走游标: autoCommit(false)+fetchSize(1000)")
    void streamingFetch() throws SQLException {
        Statement stmt = mock(Statement.class);
        Connection conn = mock(Connection.class);
        when(stmt.getConnection()).thenReturn(conn);
        new PostgresTypeProvider().applyStreamingFetch(stmt);
        verify(conn).setAutoCommit(false);
        verify(stmt).setFetchSize(1000);
    }

    @Test
    @DisplayName("validate: sslmode 必须在合法枚举内")
    void validateSslmodeEnum() {
        PostgresConnectionProperties bad = new PostgresConnectionProperties("BAD", null, null, null, null, null);
        var result = new PostgresTypeProvider().validate(bad);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0).field()).isEqualTo("sslmode");
    }

    @Test
    @DisplayName("validate: 合法 sslmode 通过")
    void validateOk() {
        PostgresConnectionProperties props = new PostgresConnectionProperties("require", null, null, null, null, 10);
        assertThat(new PostgresTypeProvider().validate(props).valid()).isTrue();
    }

    @Test
    @DisplayName("buildJdbcUrl 把 sslmode/applicationName 拼到 ?query")
    void buildJdbcUrl() {
        String json = "{\"sslmode\":\"require\",\"applicationName\":\"rudder\"}";
        String url = new PostgresTypeProvider().buildJdbcUrl("h", 5432, "db", json);
        assertThat(url).startsWith("jdbc:postgresql://h:5432/db?")
                .contains("sslmode=require")
                .contains("applicationName=rudder");
    }
}
