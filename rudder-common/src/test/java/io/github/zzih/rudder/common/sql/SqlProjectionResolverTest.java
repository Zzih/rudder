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

import java.util.List;

import org.junit.jupiter.api.Test;

class SqlProjectionResolverTest {

    @Test
    void simpleSelect() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve("SELECT phone, name FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("phone");
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void columnAlias() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve("SELECT phone AS a FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("a");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
    }

    @Test
    void trailingSemicolonAndCatalogPrefix() {
        // 用户从脚本里粘出来的 SQL 通常带末尾分号 + 联邦三段式表名,Calcite parseQuery 会因分号当多语句而抛。
        // 不修复就丢掉所有 originalColumn,COLUMN 规则只能用 result name 兜底,redaction 直接漏。
        String sql = "SELECT ds.name AS a FROM mysql.rudder.t_r_datasource ds ORDER BY ds.id;";
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(sql, SqlDialect.TRINO);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("a");
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("t_r_datasource");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("name");
    }

    @Test
    void unknownDialectFallsBackToMysql() {
        // 老 API 接受字符串方言,unknown 时退化 MYSQL Lex。enum 化后调用方需自己 SqlDialect.of(...)
        // 拿到 null 再传入,这里直接传 null 验证 null 的 fallback 行为。
        List<ResolvedColumn> out = SqlProjectionResolver.resolve("SELECT phone AS a FROM users", null);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void tableAlias() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve("SELECT u.phone FROM users u", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void subquery() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT a FROM (SELECT phone AS a FROM users) t", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("a");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
    }

    @Test
    void subqueryMultiLevel() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT x FROM (SELECT a AS x FROM (SELECT phone AS a FROM users) t1) t2", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("x");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void cte() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "WITH u AS (SELECT phone FROM users) SELECT phone FROM u", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void cteWithAliasChain() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "WITH u AS (SELECT phone AS p FROM users) SELECT p AS final_col FROM u", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("final_col");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void cteWithColumnList() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "WITH u(x) AS (SELECT phone FROM users) SELECT x FROM u", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
    }

    @Test
    void join() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT u.phone, o.total FROM users u JOIN orders o ON u.id = o.uid", SqlDialect.MYSQL);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getOriginalTable()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
        assertThat(out.get(1).getOriginalTable()).isEqualToIgnoringCase("orders");
        assertThat(out.get(1).getOriginalColumn()).isEqualToIgnoringCase("total");
    }

    @Test
    void union() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT phone FROM users UNION SELECT mobile FROM customers", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        // 列名来自第一个 arm,原始列不完全一致所以退化为派生
        assertThat(out.get(0).getResultName()).isEqualToIgnoringCase("phone");
        assertThat(out.get(0).isSimpleRef()).isFalse();
        assertThat(out.get(0).getDerivedFrom()).isNotEmpty();
    }

    @Test
    void computedColumn() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT UPPER(phone) AS p FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).isSimpleRef()).isFalse();
        assertThat(out.get(0).getDerivedFrom())
                .extracting(ResolvedColumn.SourceRef::getColumn)
                .containsExactlyInAnyOrder("phone");
    }

    @Test
    void aggregate() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "SELECT COUNT(phone) AS c FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).isAggregate()).isTrue();
    }

    @Test
    void selectStarFromCte() {
        // SELECT * 从 CTE 展开 —— 列的 original 追溯应当保留
        List<ResolvedColumn> out = SqlProjectionResolver.resolve(
                "WITH u AS (SELECT phone, email FROM users) SELECT * FROM u", SqlDialect.MYSQL);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getOriginalColumn()).isEqualToIgnoringCase("phone");
        assertThat(out.get(1).getOriginalColumn()).isEqualToIgnoringCase("email");
    }

    @Test
    void malformedSqlReturnsEmpty() {
        List<ResolvedColumn> out = SqlProjectionResolver.resolve("NOT EVEN SQL <><>", SqlDialect.MYSQL);
        assertThat(out).isEmpty();
    }
}
