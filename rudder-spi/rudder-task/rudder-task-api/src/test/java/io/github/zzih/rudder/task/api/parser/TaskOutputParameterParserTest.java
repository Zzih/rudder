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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TaskOutputParameterParserTest {

    @Test
    void singleLineDollarPrefix() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("hello ${setValue(count=42)} world");
        Map<String, String> out = toMap(p.getOutputParams());
        assertEquals("42", out.get("count"));
        // 出参方向 / 类型默认值
        Property prop = p.getOutputParams().get(0);
        assertEquals(Direct.OUT, prop.getDirect());
        assertEquals(DataType.VARCHAR, prop.getType());
    }

    @Test
    void singleLineHashPrefix() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("#{setValue(status=success)}");
        assertEquals("success", toMap(p.getOutputParams()).get("status"));
    }

    @Test
    void multipleSetValueOnOneLine() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(a=1)}${setValue(b=2)}");
        Map<String, String> out = toMap(p.getOutputParams());
        assertEquals("1", out.get("a"));
        assertEquals("2", out.get("b"));
    }

    @Test
    void mixedDollarAndHashOnSameLine() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("prefix ${setValue(x=1)} mid #{setValue(y=2)} tail");
        Map<String, String> out = toMap(p.getOutputParams());
        assertEquals("1", out.get("x"));
        assertEquals("2", out.get("y"));
    }

    @Test
    void crossLineJsonValue() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(payload={");
        p.appendParseLog("  \"user\": \"alice\",");
        p.appendParseLog("  \"amount\": 42");
        p.appendParseLog("})}");

        Map<String, String> out = toMap(p.getOutputParams());
        String payload = out.get("payload");
        assertNotNull(payload);
        assertTrue(payload.contains("alice"));
        assertTrue(payload.contains("42"));
        // 跨行内容应保留换行
        assertTrue(payload.contains("\n"));
    }

    @Test
    void duplicateKeyLastWins() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(k=v1)}");
        p.appendParseLog("${setValue(k=v2)}");
        assertEquals("v2", toMap(p.getOutputParams()).get("k"));
    }

    @Test
    void valueContainsEqualsSign() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        // value 里带 '=' — 只在第一个 = 处切
        p.appendParseLog("${setValue(query=a=1&b=2)}");
        assertEquals("a=1&b=2", toMap(p.getOutputParams()).get("query"));
    }

    @Test
    void missingEqualsSkipped() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(novalue)}");
        assertTrue(p.getOutputParams().isEmpty());
    }

    @Test
    void nullLineIgnored() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog(null);
        assertTrue(p.getOutputParams().isEmpty());
    }

    @Test
    void unrelatedLinesIgnored() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("just some log");
        p.appendParseLog("another log line");
        assertTrue(p.getOutputParams().isEmpty());
    }

    @Test
    void maxRowsExceededDropsBuffer() {
        // maxRows=3 → 持续 4 行未闭合应放弃当前 buffer
        TaskOutputParameterParser p = new TaskOutputParameterParser(3, 1024);
        p.appendParseLog("${setValue(huge=row1");
        p.appendParseLog("row2");
        p.appendParseLog("row3");
        p.appendParseLog("row4");
        // 此时应已放弃 — 后续闭合行也产不出 huge 这条 prop
        p.appendParseLog("done)}");
        assertTrue(p.getOutputParams().isEmpty());

        // 后续新的 setValue 仍可正常解析(buffer 重置过)
        p.appendParseLog("${setValue(ok=fine)}");
        assertEquals("fine", toMap(p.getOutputParams()).get("ok"));
    }

    @Test
    void maxLengthExceededDropsBuffer() {
        TaskOutputParameterParser p = new TaskOutputParameterParser(1024, 20);
        p.appendParseLog("${setValue(huge=" + "x".repeat(50));
        // length 超过 20 → 下次 append 时检测到 → 放弃
        p.appendParseLog("more");
        p.appendParseLog("end)}");
        assertTrue(p.getOutputParams().isEmpty());
    }

    @Test
    void emptyKeySkipped() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(=value)}");
        assertTrue(p.getOutputParams().isEmpty());
    }

    @Test
    void preservesInsertionOrder() {
        TaskOutputParameterParser p = new TaskOutputParameterParser();
        p.appendParseLog("${setValue(c=3)}");
        p.appendParseLog("${setValue(a=1)}");
        p.appendParseLog("${setValue(b=2)}");
        List<Property> out = p.getOutputParams();
        assertEquals("c", out.get(0).getProp());
        assertEquals("a", out.get(1).getProp());
        assertEquals("b", out.get(2).getProp());
    }

    private static Map<String, String> toMap(List<Property> props) {
        return props.stream().collect(Collectors.toMap(Property::getProp, Property::getValue));
    }
}
