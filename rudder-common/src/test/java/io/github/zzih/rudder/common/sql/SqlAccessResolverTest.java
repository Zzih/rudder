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

class SqlAccessResolverTest {

    @Test
    void nullOrBlank_returnsEmpty() {
        assertThat(SqlAccessResolver.resolve(null, SqlDialect.MYSQL)).isEmpty();
        assertThat(SqlAccessResolver.resolve("   ", SqlDialect.MYSQL)).isEmpty();
        assertThat(SqlAccessResolver.resolve(";;;", SqlDialect.MYSQL)).isEmpty();
    }

    @Test
    void selectSingleTable_returnsRead() {
        List<TableAccess> out = SqlAccessResolver.resolve("SELECT id, name FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).table()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).action()).isEqualTo(TableAccess.Action.READ);
    }

    @Test
    void selectQualifiedTable_extractsDatabase() {
        List<TableAccess> out = SqlAccessResolver.resolve("SELECT * FROM analytics.users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).database()).isEqualToIgnoringCase("analytics");
        assertThat(out.get(0).table()).isEqualToIgnoringCase("users");
    }

    @Test
    void selectThreePartName_extractsCatalogAndDatabase() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM hive.analytics.users", SqlDialect.TRINO);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).catalog()).isEqualToIgnoringCase("hive");
        assertThat(out.get(0).database()).isEqualToIgnoringCase("analytics");
        assertThat(out.get(0).table()).isEqualToIgnoringCase("users");
    }

    @Test
    void selectWithJoin_capturesBothTables() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT u.id, o.amount FROM users u JOIN orders o ON o.user_id = u.id",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("users", "orders");
        assertThat(out).extracting(TableAccess::action).containsOnly(TableAccess.Action.READ);
    }

    @Test
    void selectWithSubquery_capturesInnerTable() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT * FROM (SELECT id FROM orders WHERE amount > 100) o",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactly("orders");
    }

    @Test
    void selectWithCte_excludesCteName() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "WITH active_users AS (SELECT id FROM users WHERE active = 1) "
                        + "SELECT * FROM active_users JOIN orders ON orders.user_id = active_users.id",
                SqlDialect.MYSQL);
        // active_users 是 CTE 不算真实表;users / orders 是真实表
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("users", "orders");
    }

    @Test
    void selectWithUnion_capturesBothArms() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM users_us UNION ALL SELECT id FROM users_eu",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("users_us", "users_eu");
    }

    @Test
    void whereInSubquery_capturesInnerTable() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM users WHERE id IN (SELECT user_id FROM banned)",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("users", "banned");
    }

    @Test
    void insertIntoSelect_targetIsInsertSourceIsRead() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT INTO target_table SELECT id, name FROM source_table",
                SqlDialect.MYSQL);
        assertThat(out).hasSize(2);
        TableAccess target = out.stream()
                .filter(a -> a.action() == TableAccess.Action.INSERT)
                .findFirst().orElseThrow();
        assertThat(target.table()).isEqualToIgnoringCase("target_table");
        TableAccess source = out.stream()
                .filter(a -> a.action() == TableAccess.Action.READ)
                .findFirst().orElseThrow();
        assertThat(source.table()).isEqualToIgnoringCase("source_table");
    }

    @Test
    void hiveInsertIntoTablePartition_targetInsertSourceRead() {
        // Hive 特有 `INSERT INTO TABLE t PARTITION(...)` 写语句必须解析出 INSERT 目标 + READ 源,否则整条绕过鉴权。
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT INTO TABLE lb_bi_quality.dwd_label_err_detail_hf PARTITION (pt='20250531') "
                        + "SELECT t, business FROM lb_bi_quality.dwd_label_err_detail_hf WHERE pt='20250415'",
                SqlDialect.HIVE);
        TableAccess target = out.stream()
                .filter(a -> a.action() == TableAccess.Action.INSERT)
                .findFirst().orElseThrow();
        assertThat(target.database()).isEqualToIgnoringCase("lb_bi_quality");
        assertThat(target.table()).isEqualToIgnoringCase("dwd_label_err_detail_hf");
        TableAccess source = out.stream()
                .filter(a -> a.action() == TableAccess.Action.READ)
                .findFirst().orElseThrow();
        assertThat(source.table()).isEqualToIgnoringCase("dwd_label_err_detail_hf");
    }

    @Test
    void hiveInsertOverwriteTable_targetInsert() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT OVERWRITE TABLE t PARTITION (dt='1') SELECT id FROM src", SqlDialect.HIVE);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("t", "src");
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("t"))
                .extracting(TableAccess::action).containsExactly(TableAccess.Action.INSERT);
    }

    @Test
    void insertValues_targetOnly() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT INTO users(id, name) VALUES (1, 'a')",
                SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).table()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).action()).isEqualTo(TableAccess.Action.INSERT);
    }

    @Test
    void deleteWithWhereSubquery_capturesBoth() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "DELETE FROM users WHERE id IN (SELECT user_id FROM expired)",
                SqlDialect.MYSQL);
        TableAccess target = out.stream()
                .filter(a -> a.action() == TableAccess.Action.DELETE)
                .findFirst().orElseThrow();
        assertThat(target.table()).isEqualToIgnoringCase("users");
        TableAccess sub = out.stream()
                .filter(a -> a.action() == TableAccess.Action.READ)
                .findFirst().orElseThrow();
        assertThat(sub.table()).isEqualToIgnoringCase("expired");
    }

    @Test
    void updateBasic_capturesTargetAsUpdate() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "UPDATE users SET name = 'x' WHERE id = 1", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).table()).isEqualToIgnoringCase("users");
        assertThat(out.get(0).action()).isEqualTo(TableAccess.Action.UPDATE);
    }

    @Test
    void multipleStatements_splitAndAggregate() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM t1; INSERT INTO t2 SELECT id FROM t3;",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("t1", "t2", "t3");
    }

    @Test
    void semicolonInsideStringLiteral_notSplit() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM users WHERE name = 'a;b'",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactly("users");
    }

    @Test
    void ddl_createDropAlter_gated() {
        assertThat(SqlAccessResolver.resolve("CREATE TABLE foo (id INT)", SqlDialect.HIVE))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.table()).isEqualToIgnoringCase("foo");
                    assertThat(a.action()).isEqualTo(TableAccess.Action.CREATE);
                });
        assertThat(SqlAccessResolver.resolve("DROP TABLE foo", SqlDialect.HIVE))
                .singleElement()
                .satisfies(a -> assertThat(a.action()).isEqualTo(TableAccess.Action.DROP));
        assertThat(SqlAccessResolver.resolve("ALTER TABLE foo ADD COLUMNS (c INT)", SqlDialect.HIVE))
                .singleElement()
                .satisfies(a -> assertThat(a.action()).isEqualTo(TableAccess.Action.ALTER));
    }

    @Test
    void starrocksCreateTable_gatedAsCreate() {
        // StarRocks CREATE 经 accessFor 映射到 create_table(挂 database 层)
        List<TableAccess> out = SqlAccessResolver.resolve(
                "CREATE TABLE db.t (id INT)", SqlDialect.STARROCKS);
        assertThat(out).singleElement().satisfies(a -> {
            assertThat(a.database()).isEqualToIgnoringCase("db");
            assertThat(a.table()).isEqualToIgnoringCase("t");
            assertThat(a.action()).isEqualTo(TableAccess.Action.CREATE);
        });
    }

    @Test
    void ctas_targetCreateSourceRead() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "CREATE TABLE dst AS SELECT id FROM src", SqlDialect.HIVE);
        TableAccess dst = out.stream().filter(a -> a.action() == TableAccess.Action.CREATE)
                .findFirst().orElseThrow();
        assertThat(dst.table()).isEqualToIgnoringCase("dst");
        TableAccess src = out.stream().filter(a -> a.action() == TableAccess.Action.READ)
                .findFirst().orElseThrow();
        assertThat(src.table()).isEqualToIgnoringCase("src");
    }

    @Test
    void ddlMixedWithDml_bothGated() {
        // CREATE 与后续 SELECT 都产出 intent
        List<TableAccess> out = SqlAccessResolver.resolve(
                "CREATE TABLE foo (id INT); SELECT id FROM bar", SqlDialect.MYSQL);
        assertThat(out).extracting(a -> a.table().toLowerCase() + ":" + a.action())
                .containsExactlyInAnyOrder("foo:CREATE", "bar:READ");
    }

    @Test
    void nestedCte_excludesAllCteNames() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "WITH a AS (SELECT id FROM raw_a), b AS (SELECT id FROM a JOIN raw_b ON true) "
                        + "SELECT * FROM b",
                SqlDialect.MYSQL);
        assertThat(out).extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("raw_a", "raw_b");
    }

    @Test
    void dialectVariation_trinoBracketedIdentifier() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM \"my schema\".\"my table\"",
                SqlDialect.TRINO);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).database()).isEqualTo("my schema");
        assertThat(out.get(0).table()).isEqualTo("my table");
    }

    // ==================== 列粒度 ====================

    @Test
    void columns_singleTableSelect_attachToTable() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id, name FROM users", SqlDialect.MYSQL);
        assertThat(out).hasSize(1);
        assertThat(lower(out.get(0).columns())).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void columns_selectStar_emptyColumns() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT * FROM users", SqlDialect.MYSQL);
        assertThat(out.get(0).columns()).isEmpty();
    }

    @Test
    void columns_tableDotStar_emptyColumns() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT u.* FROM users u", SqlDialect.MYSQL);
        assertThat(out.get(0).columns()).isEmpty();
    }

    @Test
    void columns_qualifiedInJoin_routeByAlias() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT u.id, o.amount FROM users u JOIN orders o ON o.user_id = u.id",
                SqlDialect.MYSQL);
        TableAccess users = out.stream().filter(a -> a.table().equalsIgnoreCase("users")).findFirst().orElseThrow();
        TableAccess orders = out.stream().filter(a -> a.table().equalsIgnoreCase("orders")).findFirst().orElseThrow();
        // JOIN ON 列也归属:users.id + orders.user_id,加 SELECT-list u.id + o.amount
        assertThat(lower(users.columns())).containsExactlyInAnyOrder("id");
        assertThat(lower(orders.columns())).containsExactlyInAnyOrder("amount", "user_id");
    }

    @Test
    void columns_unqualifiedInJoin_degradesToTableLevel() {
        // 多表 + 未限定列 col → 无法归属,整 SELECT 列降级为空(表级)
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT col FROM users JOIN orders ON orders.user_id = users.id",
                SqlDialect.MYSQL);
        // users / orders 都降级为表级 columns=[]
        for (TableAccess t : out) {
            assertThat(t.columns()).isEmpty();
        }
    }

    @Test
    void columns_qualifiedByRealTableName_route() {
        // 没 alias 时用真实表名作为隐式 alias
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT users.id, orders.amount FROM users JOIN orders ON orders.user_id = users.id",
                SqlDialect.MYSQL);
        TableAccess users = out.stream().filter(a -> a.table().equalsIgnoreCase("users")).findFirst().orElseThrow();
        assertThat(lower(users.columns())).containsExactlyInAnyOrder("id");
    }

    @Test
    void columns_whereSingleTable_attachToTable() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT id FROM users WHERE secret_col = 1", SqlDialect.MYSQL);
        assertThat(lower(out.get(0).columns())).containsExactlyInAnyOrder("id", "secret_col");
    }

    @Test
    void columns_insertWithColumnList_attachToTarget() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT INTO users(id, name) VALUES (1, 'a')", SqlDialect.MYSQL);
        assertThat(lower(out.get(0).columns())).containsExactlyInAnyOrder("id", "name");
        assertThat(out.get(0).action()).isEqualTo(TableAccess.Action.INSERT);
    }

    @Test
    void columns_insertWithoutColumnList_empty() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "INSERT INTO users VALUES (1, 'a')", SqlDialect.MYSQL);
        assertThat(out.get(0).columns()).isEmpty();
    }

    @Test
    void columns_updateSetCols_attachToTarget() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "UPDATE users SET name = 'x', age = 18 WHERE id = 1", SqlDialect.MYSQL);
        TableAccess upd = out.stream()
                .filter(a -> a.action() == TableAccess.Action.UPDATE)
                .findFirst().orElseThrow();
        assertThat(lower(upd.columns())).containsExactlyInAnyOrder("name", "age");
    }

    @Test
    void columns_selfJoin_mergeToSameTable() {
        // self-join 同表名两 alias,列合并到同一 access entry
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT a.col1, b.col2 FROM t a JOIN t b ON a.id = b.id", SqlDialect.MYSQL);
        // 同一 (db, t, READ) 合并为 1 条
        assertThat(out).hasSize(1);
        assertThat(lower(out.get(0).columns())).containsExactlyInAnyOrder("col1", "col2", "id");
    }

    @Test
    void columns_subquery_innerScopeIndependent() {
        // 子查询自己有 scope,外层 FROM 子查询 alias 列引用不归属到内部表
        List<TableAccess> out = SqlAccessResolver.resolve(
                "SELECT o.amount FROM (SELECT amount, user_id FROM orders) o",
                SqlDialect.MYSQL);
        TableAccess orders = out.stream().filter(a -> a.table().equalsIgnoreCase("orders")).findFirst().orElseThrow();
        // 内部 SELECT 在 orders 上加 amount, user_id;外层 o.amount 找不到 alias(o 是子查询不是真表) → fail-open
        assertThat(lower(orders.columns())).containsExactlyInAnyOrder("amount", "user_id");
    }

    @Test
    void reservedWordColumn_fallbackParsesNotFailOpen() {
        // Druid 的 hive/trino parser 把 `comment` 当保留字会整句失败 → 回退 MySQL 方言恢复,避免 fail-open 绕过
        List<TableAccess> out = SqlAccessResolver.resolve("SELECT comment FROM sensitive_t", SqlDialect.HIVE);
        assertThat(out).singleElement().satisfies(a -> {
            assertThat(a.table()).isEqualToIgnoringCase("sensitive_t");
            assertThat(a.action()).isEqualTo(TableAccess.Action.READ);
        });
    }

    @Test
    void multiTableUpdate_gatesAllTargets() {
        // 多表 UPDATE 目标是 JOIN,旧实现产出空 intent → 写绕过;现各目标表表级 UPDATE
        List<TableAccess> out = SqlAccessResolver.resolve(
                "UPDATE a JOIN b ON a.id = b.id SET a.x = 1", SqlDialect.MYSQL);
        assertThat(out).extracting(a -> a.table().toLowerCase() + ":" + a.action())
                .containsExactlyInAnyOrder("a:UPDATE", "b:UPDATE");
    }

    @Test
    void updateFromSource_capturesRead() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "UPDATE t SET x = s.y FROM src s WHERE t.id = s.id", SqlDialect.POSTGRES);
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("t"))
                .extracting(TableAccess::action).containsExactly(TableAccess.Action.UPDATE);
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("src"))
                .extracting(TableAccess::action).containsExactly(TableAccess.Action.READ);
    }

    @Test
    void merge_targetWriteSourceRead() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "MERGE INTO t USING s ON t.id = s.id "
                        + "WHEN MATCHED THEN UPDATE SET t.v = s.v "
                        + "WHEN NOT MATCHED THEN INSERT (id) VALUES (s.id)",
                SqlDialect.TRINO);
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("t"))
                .extracting(TableAccess::action)
                .containsExactlyInAnyOrder(TableAccess.Action.UPDATE, TableAccess.Action.INSERT);
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("s"))
                .extracting(TableAccess::action).containsExactly(TableAccess.Action.READ);
    }

    @Test
    void replace_targetInsert() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "REPLACE INTO t(id, name) VALUES (1, 'a')", SqlDialect.MYSQL);
        assertThat(out).singleElement().satisfies(a -> {
            assertThat(a.table()).isEqualToIgnoringCase("t");
            assertThat(a.action()).isEqualTo(TableAccess.Action.INSERT);
            assertThat(lower(a.columns())).containsExactlyInAnyOrder("id", "name");
        });
    }

    @Test
    void hiveMultiInsert_sourceReadTargetsInsert() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "FROM src INSERT OVERWRITE TABLE a SELECT x INSERT OVERWRITE TABLE b SELECT y",
                SqlDialect.HIVE);
        assertThat(out).filteredOn(a -> a.action() == TableAccess.Action.INSERT)
                .extracting(TableAccess::table).extracting(String::toLowerCase)
                .containsExactlyInAnyOrder("a", "b");
        assertThat(out).filteredOn(a -> a.table().equalsIgnoreCase("src"))
                .extracting(TableAccess::action).containsExactly(TableAccess.Action.READ);
    }

    @Test
    void loadData_targetInsert() {
        List<TableAccess> out = SqlAccessResolver.resolve(
                "LOAD DATA INPATH '/tmp/x' INTO TABLE t", SqlDialect.HIVE);
        assertThat(out).singleElement().satisfies(a -> {
            assertThat(a.table()).isEqualToIgnoringCase("t");
            assertThat(a.action()).isEqualTo(TableAccess.Action.INSERT);
        });
    }

    @Test
    void allDialects_basicSelectAndInsertResolve() {
        for (SqlDialect d : SqlDialect.values()) {
            List<TableAccess> sel = SqlAccessResolver.resolve("SELECT col FROM db.t WHERE x = 1", d);
            assertThat(sel).as("select on %s", d).hasSize(1);
            assertThat(sel.get(0).table()).isEqualToIgnoringCase("t");
            assertThat(sel.get(0).action()).isEqualTo(TableAccess.Action.READ);
            assertThat(lower(sel.get(0).columns())).containsExactlyInAnyOrder("col", "x");

            List<TableAccess> ins = SqlAccessResolver.resolve("INSERT INTO t SELECT a FROM s", d);
            assertThat(ins).as("insert on %s", d).extracting(TableAccess::table)
                    .extracting(String::toLowerCase).containsExactlyInAnyOrder("t", "s");
        }
    }

    private static List<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase()).toList();
    }
}
