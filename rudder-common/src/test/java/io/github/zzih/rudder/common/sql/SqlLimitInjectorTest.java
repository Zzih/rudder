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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlLimitInjectorTest {

    @Test
    void simpleSelectAppendsLimit() {
        String out = SqlLimitInjector.inject("SELECT * FROM users", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users\nLIMIT 100");
    }

    @Test
    void existingLimitSkipped() {
        String sql = "SELECT * FROM users LIMIT 10";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void orderByWithoutLimitAppendsLimit() {
        String out = SqlLimitInjector.inject("SELECT * FROM users ORDER BY id", 50, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users ORDER BY id\nLIMIT 50");
    }

    @Test
    void orderByWithLimitSkipped() {
        String sql = "SELECT * FROM users ORDER BY id LIMIT 10";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void withCteAppendsLimitAfterBody() {
        String sql = "WITH t AS (SELECT * FROM foo) SELECT * FROM t";
        String out = SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo(sql + "\nLIMIT 100");
    }

    @Test
    void withCteAlreadyLimitedSkipped() {
        String sql = "WITH t AS (SELECT * FROM foo) SELECT * FROM t LIMIT 5";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void unionAppendsLimit() {
        String sql = "SELECT a FROM t1 UNION SELECT b FROM t2";
        String out = SqlLimitInjector.inject(sql, 100, SqlDialect.TRINO);
        assertThat(out).isEqualTo(sql + "\nLIMIT 100");
    }

    @Test
    void insertSkipped() {
        String sql = "INSERT INTO t SELECT * FROM s";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void hiveSetCommandSkipped() {
        String sql = "SET hive.execution.engine=tez";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.HIVE)).isEqualTo(sql);
    }

    @Test
    void trailingSemicolonPreserved() {
        String out = SqlLimitInjector.inject("SELECT * FROM users;", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users\nLIMIT 100;");
    }

    @Test
    void jdbcPlaceholderTolerated() {
        String out = SqlLimitInjector.inject("SELECT * FROM users WHERE id = ?", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users WHERE id = ?\nLIMIT 100");
    }

    @Test
    void parseFailureReturnsOriginal() {
        String sql = "SELECT FROM WHERE garbage";
        assertThat(SqlLimitInjector.inject(sql, 100, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void nonPositiveLimitSkipped() {
        String sql = "SELECT * FROM users";
        assertThat(SqlLimitInjector.inject(sql, 0, SqlDialect.MYSQL)).isEqualTo(sql);
        assertThat(SqlLimitInjector.inject(sql, -1, SqlDialect.MYSQL)).isEqualTo(sql);
    }

    @Test
    void nullAndBlankSafe() {
        assertThat(SqlLimitInjector.inject(null, 100, SqlDialect.MYSQL)).isNull();
        assertThat(SqlLimitInjector.inject("", 100, SqlDialect.MYSQL)).isEmpty();
        assertThat(SqlLimitInjector.inject("   ", 100, SqlDialect.MYSQL)).isEqualTo("   ");
    }

    @Test
    void nullDialectFallsBackToMysqlLex() {
        String out = SqlLimitInjector.inject("SELECT * FROM users", 100, null);
        assertThat(out).isEqualTo("SELECT * FROM users\nLIMIT 100");
    }

    @Test
    void trinoWithCatalogSchemaAppendsLimit() {
        String sql = "SELECT * FROM hive.lb_bi_activity.ads_deposit_details";
        String out = SqlLimitInjector.inject(sql, 1000, SqlDialect.TRINO);
        assertThat(out).isEqualTo(sql + "\nLIMIT 1000");
    }

    /** -- 行注释吞掉 LIMIT 是这次审计发现的高危 bug — 必须前置换行才能让 LIMIT 落到下一行。 */
    @Test
    void trailingDashCommentDoesNotSwallowLimit() {
        String out = SqlLimitInjector.inject("SELECT * FROM users -- trailing comment", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users -- trailing comment\nLIMIT 100");
    }

    @Test
    void trailingDashCommentMultiLineDoesNotSwallowLimit() {
        String out = SqlLimitInjector.inject("SELECT * FROM users\n-- last line", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users\n-- last line\nLIMIT 100");
    }

    /** MySQL `#` 行注释 Druid 能识别;LIMIT 前置换行落到下一行,不被注释吞掉。 */
    @Test
    void mysqlHashCommentAppendsLimitOnNewLine() {
        String out = SqlLimitInjector.inject("SELECT * FROM users # mysql comment", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("SELECT * FROM users # mysql comment\nLIMIT 100");
    }

    /** 括号包裹的顶层 SELECT:解析剥离外层括号,LIMIT 追加在括号外,主流方言都接受。 */
    @Test
    void parenthesizedSelectAppendsLimitOutsideParens() {
        String out = SqlLimitInjector.inject("(SELECT * FROM users)", 100, SqlDialect.MYSQL);
        assertThat(out).isEqualTo("(SELECT * FROM users)\nLIMIT 100");
    }
}
