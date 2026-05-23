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

package io.github.zzih.rudder.datasource.hive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HiveTypeProviderTest {

    @Test
    @DisplayName("type/dialect 与 HIVE 一致")
    void typeAndDialect() {
        HiveTypeProvider p = new HiveTypeProvider();
        assertThat(p.dbType()).isEqualTo(DatasourceType.HIVE);
        assertThat(p.dialect()).isEqualTo(SqlDialect.HIVE);
    }

    @Test
    @DisplayName("buildPreviewSql 无反引号")
    void previewNoBackticks() {
        assertThat(new HiveTypeProvider().buildPreviewSql("db", "t", 10))
                .isEqualTo("SELECT * FROM db.t LIMIT 10");
    }

    @Test
    @DisplayName("applyStreamingFetch fetchSize=1000")
    void streamingFetch() throws SQLException {
        Statement stmt = mock(Statement.class);
        new HiveTypeProvider().applyStreamingFetch(stmt);
        verify(stmt).setFetchSize(1000);
    }

    @Test
    @DisplayName("buildJdbcUrl 用 Beeline 分号风格(;auth=;principal=)")
    void buildJdbcUrlSemicolonStyle() {
        String json = "{\"auth\":\"KERBEROS\",\"principal\":\"hive/host@REALM\"}";
        String url = new HiveTypeProvider().buildJdbcUrl("h", 10000, "default", json);

        assertThat(url).startsWith("jdbc:hive2://h:10000/default")
                .contains(";auth=KERBEROS")
                .contains(";principal=hive/host@REALM")
                .doesNotContain("?"); // 分号风格不应有 ?
    }

    @Test
    @DisplayName("validate: auth=KERBEROS 时 principal 必填")
    void validateRequirePrincipal() {
        HiveConnectionProperties props = new HiveConnectionProperties("KERBEROS", null, null, null, null, null);
        var result = new HiveTypeProvider().validate(props);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0).field()).isEqualTo("principal");
    }

    @Test
    @DisplayName("validate: auth 必须是 NONE/KERBEROS/LDAP")
    void validateAuthEnum() {
        HiveConnectionProperties props = new HiveConnectionProperties("BAD", null, null, null, null, null);
        var result = new HiveTypeProvider().validate(props);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0).field()).isEqualTo("auth");
    }

    @Test
    @DisplayName("validate: 合法 props 通过")
    void validateOk() {
        HiveConnectionProperties props = new HiveConnectionProperties("NONE", null, null, null, "binary", null);
        assertThat(new HiveTypeProvider().validate(props).valid()).isTrue();
    }

    @Test
    @DisplayName("buildJdbcUrl: keytabPath / krb5ConfPath 不拼 URL(Kerberos 系统级配置)+ auth=NONE 不拼")
    void buildJdbcUrlExcludesKerberosFiles() {
        String json = "{\"auth\":\"NONE\",\"keytabPath\":\"/etc/hive.keytab\",\"krb5ConfPath\":\"/etc/krb5.conf\"}";
        String url = new HiveTypeProvider().buildJdbcUrl("h", 10000, "default", json);

        assertThat(url).doesNotContain("keytabPath")
                .doesNotContain("krb5ConfPath")
                .doesNotContain("auth=NONE");
    }
}
