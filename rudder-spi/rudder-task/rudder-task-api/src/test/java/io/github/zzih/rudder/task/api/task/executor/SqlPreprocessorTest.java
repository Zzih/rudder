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

package io.github.zzih.rudder.task.api.task.executor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.param.Property;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SqlPreprocessorTest {

    @Test
    void simpleVarBecomesQuestionMark() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "SELECT * FROM users WHERE id = ${user_id}",
                Map.of("user_id", prop("user_id", DataType.INTEGER, "42")));

        assertEquals("SELECT * FROM users WHERE id = ?", p.sql());
        assertEquals(1, p.binds().size());
        assertEquals("42", p.binds().get(0).getValue());
        assertEquals(DataType.INTEGER, p.binds().get(0).getType());
    }

    @Test
    void rawValueReplacement() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "SELECT * FROM !{tbl} WHERE x = ${val}",
                Map.of("tbl", prop("tbl", DataType.VARCHAR, "users"),
                        "val", prop("val", DataType.INTEGER, "1")));

        assertEquals("SELECT * FROM users WHERE x = ?", p.sql());
        assertEquals(1, p.binds().size());
        assertEquals("1", p.binds().get(0).getValue());
    }

    @Test
    void rawValueOnlyNoBinds() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "ORDER BY !{sort_col} !{sort_dir}",
                Map.of("sort_col", prop("sort_col", DataType.VARCHAR, "created_at"),
                        "sort_dir", prop("sort_dir", DataType.VARCHAR, "DESC")));

        assertEquals("ORDER BY created_at DESC", p.sql());
        assertTrue(p.binds().isEmpty());
    }

    @Test
    void listExpansion() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "SELECT * FROM t WHERE id IN (${ids})",
                Map.of("ids", prop("ids", DataType.LIST, "[1, 2, 3]")));

        assertEquals("SELECT * FROM t WHERE id IN (?,?,?)", p.sql());
        assertEquals(3, p.binds().size());
        assertEquals("1", p.binds().get(0).getValue());
        assertEquals("2", p.binds().get(1).getValue());
        assertEquals("3", p.binds().get(2).getValue());
        assertEquals(DataType.INTEGER, p.binds().get(0).getType());
    }

    @Test
    void emptyListBecomesNull() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "SELECT * FROM t WHERE id IN (${ids})",
                Map.of("ids", prop("ids", DataType.LIST, "[]")));

        assertEquals("SELECT * FROM t WHERE id IN (NULL)", p.sql());
        assertTrue(p.binds().isEmpty());
    }

    @Test
    void listOfStrings() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "WHERE name IN (${names})",
                Map.of("names", prop("names", DataType.LIST, "[\"alice\", \"bob\"]")));

        assertEquals("WHERE name IN (?,?)", p.sql());
        assertEquals(2, p.binds().size());
        assertEquals("alice", p.binds().get(0).getValue());
        assertEquals(DataType.VARCHAR, p.binds().get(0).getType());
    }

    @Test
    void multipleVarsKeepOrder() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "WHERE a = ${a} AND b = ${b} AND a2 = ${a}",
                Map.of("a", prop("a", DataType.INTEGER, "1"),
                        "b", prop("b", DataType.VARCHAR, "x")));

        assertEquals("WHERE a = ? AND b = ? AND a2 = ?", p.sql());
        assertEquals(3, p.binds().size());
        // 同名变量出现两次,binds 也要重复两次(每个 ? 都要绑)
        assertEquals("1", p.binds().get(0).getValue());
        assertEquals("x", p.binds().get(1).getValue());
        assertEquals("1", p.binds().get(2).getValue());
    }

    @Test
    void unresolvedDollarVarKeptAsIs() {
        // 参数 map 里没有 user_id → 占位符不解析,留原样,让 driver 后续报错(比静默 NULL 安全)
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "WHERE id = ${user_id}",
                Map.of());
        assertEquals("WHERE id = ${user_id}", p.sql());
        assertTrue(p.binds().isEmpty());
    }

    @Test
    void unresolvedRawVarKeptAsIs() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "FROM !{tbl}",
                Map.of());
        assertEquals("FROM !{tbl}", p.sql());
    }

    @Test
    void rawAndListMixed() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "SELECT * FROM !{tbl} WHERE id IN (${ids}) AND status = ${status}",
                Map.of(
                        "tbl", prop("tbl", DataType.VARCHAR, "orders"),
                        "ids", prop("ids", DataType.LIST, "[100, 200]"),
                        "status", prop("status", DataType.VARCHAR, "OK")));

        assertEquals("SELECT * FROM orders WHERE id IN (?,?) AND status = ?", p.sql());
        assertEquals(3, p.binds().size());
    }

    @Test
    void nullParamMapNoChange() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess("SELECT 1", null);
        assertEquals("SELECT 1", p.sql());
        assertTrue(p.binds().isEmpty());
    }

    @Test
    void emptySqlPassthrough() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess("", Map.of());
        assertEquals("", p.sql());
        assertTrue(p.binds().isEmpty());
    }

    @Test
    void indexByPropFromList() {
        Map<String, Property> idx = SqlPreprocessor.indexByProp(List.of(
                prop("a", DataType.INTEGER, "1"),
                prop("b", DataType.VARCHAR, "x")));
        assertEquals(2, idx.size());
        assertEquals("1", idx.get("a").getValue());
    }

    @Test
    void quotedDollarVarConsumesQuotes() {
        // 'user' 字面量陷阱:DS 行为是引号被一起吃掉,? 成为参数槽位 + setString 正确加引号
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "WHERE name = '${user}'",
                Map.of("user", prop("user", DataType.VARCHAR, "alice")));

        assertEquals("WHERE name = ?", p.sql());
        assertEquals(1, p.binds().size());
        assertEquals("alice", p.binds().get(0).getValue());
    }

    @Test
    void quotedDollarVarDoubleQuotes() {
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "WHERE name = \"${user}\"",
                Map.of("user", prop("user", DataType.VARCHAR, "alice")));
        assertEquals("WHERE name = ?", p.sql());
    }

    @Test
    void quotedRawValueStripsQuotes() {
        // '!{tbl}' 整体替换成 users(不带引号 — 表名不需要)
        SqlPreprocessor.Prepared p = SqlPreprocessor.preprocess(
                "FROM '!{tbl}'",
                Map.of("tbl", prop("tbl", DataType.VARCHAR, "users")));
        assertEquals("FROM users", p.sql());
    }

    private static Property prop(String name, DataType type, String value) {
        return Property.builder().prop(name).direct(Direct.IN).type(type).value(value).build();
    }
}
