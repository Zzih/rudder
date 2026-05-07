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

package io.github.zzih.rudder.common.utils.placeholder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PlaceholderUtilsTest {

    @Test
    void testReplaceSinglePlaceholder() {
        Map<String, String> params = Map.of("db", "prod_db");
        String result = PlaceholderUtils.replacePlaceholders("USE ${db}", params, false);
        assertEquals("USE prod_db", result);
    }

    @Test
    void testReplaceMultiplePlaceholders() {
        Map<String, String> params = Map.of("db", "prod_db", "table", "orders");
        String result = PlaceholderUtils.replacePlaceholders("SELECT * FROM ${db}.${table}", params, false);
        assertEquals("SELECT * FROM prod_db.orders", result);
    }

    @Test
    void testIgnoreUnresolvableTrue() {
        Map<String, String> params = Map.of("db", "prod_db");
        String result = PlaceholderUtils.replacePlaceholders("USE ${db} AND ${missing}", params, true);
        assertEquals("USE prod_db AND ${missing}", result);
    }

    @Test
    void testIgnoreUnresolvableFalse() {
        Map<String, String> params = Map.of("db", "prod_db");
        assertThrows(IllegalArgumentException.class,
                () -> PlaceholderUtils.replacePlaceholders("USE ${db} AND ${missing}", params, false));
    }

    @Test
    void testNullText() {
        assertNull(PlaceholderUtils.replacePlaceholders(null, Map.of("k", "v"), true));
    }

    @Test
    void testEmptyText() {
        assertEquals("", PlaceholderUtils.replacePlaceholders("", Map.of("k", "v"), true));
    }

    @Test
    void testNoPlaceholders() {
        String text = "SELECT 1";
        assertEquals(text, PlaceholderUtils.replacePlaceholders(text, Map.of("k", "v"), true));
    }

    @Test
    void testNullParams() {
        String text = "USE ${db}";
        assertEquals(text, PlaceholderUtils.replacePlaceholders(text, null, true));
    }

    @Test
    void testEmptyParams() {
        String text = "USE ${db}";
        assertEquals(text, PlaceholderUtils.replacePlaceholders(text, Map.of(), true));
    }

    @Test
    void testSpecialCharactersInValue() {
        Map<String, String> params = Map.of("path", "/data/$output/file");
        String result = PlaceholderUtils.replacePlaceholders("hdfs://${path}", params, true);
        assertEquals("hdfs:///data/$output/file", result);
    }
}
