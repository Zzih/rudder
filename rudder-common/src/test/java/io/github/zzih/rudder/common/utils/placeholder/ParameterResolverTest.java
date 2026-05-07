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

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ParameterResolverTest {

    private Date createTestDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.APRIL, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Test
    void testResolveAll_bothPlaceholders() {
        Date date = createTestDate();
        Map<String, String> params = Map.of("db", "prod_db");
        String text = "SELECT * FROM ${db}.orders WHERE dt='$[yyyyMMdd]'";
        String result = ParameterResolver.resolveAll(text, params, date);
        assertEquals("SELECT * FROM prod_db.orders WHERE dt='20260404'", result);
    }

    @Test
    void testResolveAll_onlyVarPlaceholders() {
        Map<String, String> params = Map.of("db", "prod_db", "table", "users");
        String text = "SELECT * FROM ${db}.${table}";
        String result = ParameterResolver.resolveAll(text, params, null);
        assertEquals("SELECT * FROM prod_db.users", result);
    }

    @Test
    void testResolveAll_onlyTimePlaceholders() {
        Date date = createTestDate();
        String text = "dt='$[yyyyMMdd]' AND dt_prev='$[yyyyMMdd-1]'";
        String result = ParameterResolver.resolveAll(text, null, date);
        assertEquals("dt='20260404' AND dt_prev='20260403'", result);
    }

    @Test
    void testResolveAll_noPlaceholders() {
        String text = "SELECT 1";
        String result = ParameterResolver.resolveAll(text, null, null);
        assertEquals("SELECT 1", result);
    }

    @Test
    void testResolveAll_nullText() {
        assertNull(ParameterResolver.resolveAll(null, Map.of("k", "v"), new Date()));
    }

    @Test
    void testResolveAll_emptyText() {
        assertEquals("", ParameterResolver.resolveAll("", Map.of("k", "v"), new Date()));
    }

    @Test
    void testResolveAll_unresolvedVarKept() {
        Date date = createTestDate();
        Map<String, String> params = Map.of("db", "prod_db");
        String text = "SELECT * FROM ${db}.${missing_table} WHERE dt='$[yyyyMMdd]'";
        String result = ParameterResolver.resolveAll(text, params, date);
        assertEquals("SELECT * FROM prod_db.${missing_table} WHERE dt='20260404'", result);
    }

    @Test
    void testResolveAll_addMonths() {
        Date date = createTestDate();
        String text = "partition_month='$[add_months(yyyyMMdd,-1)]'";
        String result = ParameterResolver.resolveAll(text, null, date);
        assertEquals("partition_month='20260304'", result);
    }

    @Test
    void testResolveAll_monthBegin() {
        Date date = createTestDate();
        String text = "month_start='$[month_begin(yyyyMMdd,0)]'";
        String result = ParameterResolver.resolveAll(text, null, date);
        assertEquals("month_start='20260401'", result);
    }

    @Test
    void testResolveAll_monthEnd() {
        Date date = createTestDate();
        String text = "month_end='$[month_end(yyyyMMdd,0)]'";
        String result = ParameterResolver.resolveAll(text, null, date);
        assertEquals("month_end='20260430'", result);
    }

    @Test
    void testResolveAll_complexSql() {
        Date date = createTestDate();
        Map<String, String> params = Map.of("db", "dw", "env", "prod");
        String text = """
                INSERT INTO ${db}.fact_orders_${env}
                SELECT * FROM ${db}.ods_orders_${env}
                WHERE dt >= '$[month_begin(yyyyMMdd,0)]'
                  AND dt <= '$[month_end(yyyyMMdd,0)]'
                  AND etl_date = '$[yyyyMMdd-1]'
                """;
        String result = ParameterResolver.resolveAll(text, params, date);
        assertTrue(result.contains("dw.fact_orders_prod"));
        assertTrue(result.contains("dw.ods_orders_prod"));
        assertTrue(result.contains("20260401")); // month_begin
        assertTrue(result.contains("20260430")); // month_end
        assertTrue(result.contains("20260403")); // yesterday
    }

    @Test
    void testResolveAll_tomorrowPlaceholder() {
        Date date = createTestDate();
        String text = "$[yyyyMMdd+1]";
        String result = ParameterResolver.resolveAll(text, null, date);
        assertEquals("20260405", result);
    }

    @Test
    void testResolveAll_jarParamsJson() {
        Date date = createTestDate();
        Map<String, String> params = Map.of("env", "prod");
        // JAR 任务 content 是 JSON，占位符嵌在 JSON 值中
        String json = """
                {"mainClass":"com.example.App","args":"--date $[yyyyMMdd] --env ${env}","jarPath":"/jars/app.jar"}""";
        String result = ParameterResolver.resolveAll(json, params, date);
        assertTrue(result.contains("--date 20260404"));
        assertTrue(result.contains("--env prod"));
        assertTrue(result.contains("\"mainClass\":\"com.example.App\""));
    }

    @Test
    void testResolveAll_shellScript() {
        Date date = createTestDate();
        Map<String, String> params = Map.of("table", "orders");
        String script = "#!/bin/bash\nhive -e \"SELECT * FROM ${table} WHERE dt='$[yyyyMMdd-1]'\"";
        String result = ParameterResolver.resolveAll(script, params, date);
        assertTrue(result.contains("FROM orders"));
        assertTrue(result.contains("dt='20260403'"));
    }
}
