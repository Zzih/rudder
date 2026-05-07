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

package io.github.zzih.rudder.task.api.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.param.Property;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VarPoolFilterTest {

    @Test
    void filterByMap_keepsOnlyDeclaredOut() {
        // Shell stdout 解了 3 个 setValue 候选,但 spec 只声明了 1 个 OUT
        List<Property> raw = List.of(
                prop("count", Direct.OUT, DataType.VARCHAR, "42"),
                prop("status", Direct.OUT, DataType.VARCHAR, "ok"),
                prop("debug", Direct.OUT, DataType.VARCHAR, "noise"));
        List<Property> spec = List.of(
                prop("count", Direct.OUT, DataType.INTEGER, null));

        List<Property> out = VarPoolFilter.filterByMap(raw, spec);
        assertEquals(1, out.size());
        assertEquals("count", out.get(0).getProp());
        assertEquals("42", out.get(0).getValue());
        // 类型来自 spec(声明的 INTEGER),不是 raw 的 VARCHAR
        assertEquals(DataType.INTEGER, out.get(0).getType());
        assertEquals(Direct.OUT, out.get(0).getDirect());
    }

    @Test
    void filterByMap_specInDirectionDropped() {
        // spec 里声明的是 IN 不是 OUT — 一律不进 varPool
        List<Property> raw = List.of(prop("x", Direct.OUT, DataType.VARCHAR, "1"));
        List<Property> spec = List.of(prop("x", Direct.IN, DataType.VARCHAR, null));
        assertTrue(VarPoolFilter.filterByMap(raw, spec).isEmpty());
    }

    @Test
    void filterByMap_emptySpecProducesEmpty() {
        List<Property> raw = List.of(prop("x", Direct.OUT, DataType.VARCHAR, "1"));
        assertTrue(VarPoolFilter.filterByMap(raw, List.of()).isEmpty());
        assertTrue(VarPoolFilter.filterByMap(raw, null).isEmpty());
    }

    @Test
    void filterByMap_specOutWithoutRawValueFallsBackToDeclaredDefault() {
        // spec 声明 total OUT default="0",raw 没有 total → 回退到 spec 默认值(DS 行为)
        List<Property> raw = List.of(prop("count", Direct.OUT, DataType.VARCHAR, "42"));
        List<Property> spec = List.of(prop("total", Direct.OUT, DataType.INTEGER, "0"));

        List<Property> out = VarPoolFilter.filterByMap(raw, spec);
        assertEquals(1, out.size());
        assertEquals("total", out.get(0).getProp());
        assertEquals("0", out.get(0).getValue());
        assertEquals(DataType.INTEGER, out.get(0).getType());
    }

    @Test
    void filterByMap_specOutWithoutRawValueAndNoDefaultPreservedAsNull() {
        // 两边都为 null → 仍保留 Property(value=null) — 对齐 DS dealOutParam:
        // 所有声明 OUT 一律进 varPool,这样下游 ${prop} 至少能解析到索引项
        List<Property> raw = List.of(prop("count", Direct.OUT, DataType.VARCHAR, "42"));
        List<Property> spec = List.of(prop("total", Direct.OUT, DataType.INTEGER, null));

        List<Property> out = VarPoolFilter.filterByMap(raw, spec);
        assertEquals(1, out.size());
        assertEquals("total", out.get(0).getProp());
        assertNull(out.get(0).getValue());
        assertEquals(DataType.INTEGER, out.get(0).getType());
    }

    @Test
    void filterByRow_takesValueByPropName() {
        // SQL firstRow 场景:row 的 column name 跟 spec.prop 同名才命中
        Map<String, Object> firstRow = Map.of("total", 42, "name", "alice");
        List<Property> spec = List.of(
                prop("total", Direct.OUT, DataType.INTEGER, null),
                prop("name", Direct.OUT, DataType.VARCHAR, null));

        List<Property> out = VarPoolFilter.filterByRow(firstRow, spec);
        assertEquals(2, out.size());
        Map<String, String> byProp = out.stream()
                .collect(java.util.stream.Collectors.toMap(Property::getProp, Property::getValue));
        assertEquals("42", byProp.get("total"));
        assertEquals("alice", byProp.get("name"));
    }

    @Test
    void filterByRow_unmatchedColumnNotDeclared() {
        // SQL 结果集有 unused 列,但 spec 只声明 total → 其他列丢
        Map<String, Object> firstRow = Map.of("total", 42, "ignore_me", "noise");
        List<Property> spec = List.of(prop("total", Direct.OUT, DataType.INTEGER, null));

        List<Property> out = VarPoolFilter.filterByRow(firstRow, spec);
        assertEquals(1, out.size());
        assertEquals("total", out.get(0).getProp());
    }

    @Test
    void filterByRow_nullOrEmptyRowStillProducesDeclaredOuts() {
        // null/empty row 时 raw 没值,spec 也无 default → 仍按 DS 行为产出 null Property
        List<Property> spec = List.of(prop("total", Direct.OUT, DataType.INTEGER, null));
        List<Property> outNull = VarPoolFilter.filterByRow(null, spec);
        assertEquals(1, outNull.size());
        assertNull(outNull.get(0).getValue());

        List<Property> outEmpty = VarPoolFilter.filterByRow(Map.of(), spec);
        assertEquals(1, outEmpty.size());
        assertNull(outEmpty.get(0).getValue());
    }

    @Test
    void filterByRow_nullColumnValuePreservedAsNull() {
        // 列存在但 value 为 null → 保留 Property(value=null),对齐 DS dealOutParam
        Map<String, Object> firstRow = new java.util.HashMap<>();
        firstRow.put("total", null);
        List<Property> spec = List.of(prop("total", Direct.OUT, DataType.INTEGER, null));

        List<Property> out = VarPoolFilter.filterByRow(firstRow, spec);
        assertEquals(1, out.size());
        assertNull(out.get(0).getValue());
    }

    private static Property prop(String name, Direct direct, DataType type, String value) {
        return Property.builder().prop(name).direct(direct).type(type).value(value).build();
    }
}
