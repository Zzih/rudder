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

package io.github.zzih.rudder.datasource.flink;

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

class FlinkTypeProviderTest {

    @Test
    @DisplayName("type/dialect 与 FLINK 一致")
    void typeAndDialect() {
        FlinkTypeProvider p = new FlinkTypeProvider();
        assertThat(p.dbType()).isEqualTo(DatasourceType.FLINK);
        assertThat(p.dialect()).isEqualTo(SqlDialect.FLINK);
    }

    @Test
    @DisplayName("validateConnection 走 SELECT 1(Flink JDBC 不支持 isValid)")
    void validateUsesSelectOne() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute("SELECT 1")).thenReturn(true);
        assertThat(new FlinkTypeProvider().validateConnection(conn)).isTrue();
        verify(stmt).execute("SELECT 1");
    }

    @Test
    @DisplayName("applyStreamingFetch fetchSize=1000")
    void streamingFetch() throws SQLException {
        Statement stmt = mock(Statement.class);
        new FlinkTypeProvider().applyStreamingFetch(stmt);
        verify(stmt).setFetchSize(1000);
    }
}
