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

import org.junit.jupiter.api.Test;

class TimePlaceholderUtilsTest {

    /**
     * Create a fixed date for testing: 2026-04-04 (Saturday)
     */
    private Date createTestDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.APRIL, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Test
    void testSimpleFormat_yyyyMMdd() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("yyyyMMdd", date, false);
        assertEquals("20260404", result);
    }

    @Test
    void testSimpleFormat_dashSeparated() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("yyyy-MM-dd", date, false);
        assertEquals("2026-04-04", result);
    }

    @Test
    void testSimpleFormat_HHmmss() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("HHmmss", date, false);
        assertEquals("000000", result);
    }

    @Test
    void testDayOffsetPlus1() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("yyyyMMdd+1", date, false);
        assertEquals("20260405", result);
    }

    @Test
    void testDayOffsetMinus1() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("yyyyMMdd-1", date, false);
        assertEquals("20260403", result);
    }

    @Test
    void testDayOffsetWithExpression() {
        Date date = createTestDate();
        // 7*2 = 14 days
        String result = TimePlaceholderUtils.formatTimeExpression("yyyyMMdd+7*2", date, false);
        assertEquals("20260418", result);
    }

    @Test
    void testAddMonths_minusOne() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("add_months(yyyyMMdd,-1)", date, false);
        assertEquals("20260304", result);
    }

    @Test
    void testAddMonths_plusTwo() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("add_months(yyyy-MM-dd,2)", date, false);
        assertEquals("2026-06-04", result);
    }

    @Test
    void testMonthBegin_zeroOffset() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("month_begin(yyyyMMdd,0)", date, false);
        assertEquals("20260401", result);
    }

    @Test
    void testMonthBegin_withOffset() {
        Date date = createTestDate();
        // First day + 5 days
        String result = TimePlaceholderUtils.formatTimeExpression("month_begin(yyyyMMdd,5)", date, false);
        assertEquals("20260406", result);
    }

    @Test
    void testMonthEnd_zeroOffset() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("month_end(yyyyMMdd,0)", date, false);
        assertEquals("20260430", result);
    }

    @Test
    void testMonthEnd_withOffset() {
        Date date = createTestDate();
        // Last day - 1 day
        String result = TimePlaceholderUtils.formatTimeExpression("month_end(yyyyMMdd,-1)", date, false);
        assertEquals("20260429", result);
    }

    @Test
    void testWeekBegin_zeroOffset() {
        // 2026-04-04 is Saturday, Monday is 2026-03-30
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("week_begin(yyyyMMdd,0)", date, false);
        assertEquals("20260330", result);
    }

    @Test
    void testWeekEnd_zeroOffset() {
        // 2026-04-04 is Saturday, Sunday is 2026-04-05
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("week_end(yyyyMMdd,0)", date, false);
        assertEquals("20260405", result);
    }

    @Test
    void testThisDay() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("this_day(yyyy-MM-dd)", date, false);
        assertEquals("2026-04-04", result);
    }

    @Test
    void testLastDay() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("last_day(yyyy-MM-dd)", date, false);
        assertEquals("2026-04-03", result);
    }

    @Test
    void testTimestamp() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("timestamp(yyyyMMdd)", date, false);
        // 2026-04-04 00:00:00 epoch seconds
        long expected = date.getTime() / 1000;
        assertEquals(String.valueOf(expected), result);
    }

    @Test
    void testNullDate() {
        assertThrows(IllegalArgumentException.class,
                () -> TimePlaceholderUtils.formatTimeExpression("yyyyMMdd", null, false));
    }

    @Test
    void testInvalidExpression_ignoreTrue() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("not_a_function(bad)", date, true);
        // Should return original since it is an unrecognized function-like expression,
        // but it might try to parse it as a format. If parsing fails, ignoreInvalid=true returns original.
        assertNotNull(result);
    }

    @Test
    void testEmptyExpression_ignoreTrue() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("", date, true);
        assertEquals("", result);
    }

    @Test
    void testCalculateExpression() {
        assertEquals(14, TimePlaceholderUtils.calculateExpression("7*2"));
        assertEquals(5, TimePlaceholderUtils.calculateExpression("3+2"));
        assertEquals(1, TimePlaceholderUtils.calculateExpression("3-2"));
        assertEquals(6, TimePlaceholderUtils.calculateExpression("2*3"));
        assertEquals(2, TimePlaceholderUtils.calculateExpression("6/3"));
        assertEquals(-1, TimePlaceholderUtils.calculateExpression("-1"));
        assertEquals(7, TimePlaceholderUtils.calculateExpression("(3+4)"));
        assertEquals(14, TimePlaceholderUtils.calculateExpression("(3+4)*2"));
    }

    // ===== DS 兼容别名:month_first_day / month_last_day / week_first_day / week_last_day =====

    @Test
    void testMonthFirstDay_alias() {
        // month_first_day 是 month_begin 的 DS 别名
        Date date = createTestDate();
        assertEquals(
                TimePlaceholderUtils.formatTimeExpression("month_begin(yyyyMMdd,0)", date, false),
                TimePlaceholderUtils.formatTimeExpression("month_first_day(yyyyMMdd,0)", date, false));
    }

    @Test
    void testMonthLastDay_alias() {
        Date date = createTestDate();
        assertEquals(
                TimePlaceholderUtils.formatTimeExpression("month_end(yyyyMMdd,-1)", date, false),
                TimePlaceholderUtils.formatTimeExpression("month_last_day(yyyyMMdd,-1)", date, false));
    }

    @Test
    void testWeekFirstDay_alias() {
        Date date = createTestDate();
        assertEquals(
                TimePlaceholderUtils.formatTimeExpression("week_begin(yyyyMMdd,0)", date, false),
                TimePlaceholderUtils.formatTimeExpression("week_first_day(yyyyMMdd,0)", date, false));
    }

    @Test
    void testWeekLastDay_alias() {
        Date date = createTestDate();
        assertEquals(
                TimePlaceholderUtils.formatTimeExpression("week_end(yyyyMMdd,0)", date, false),
                TimePlaceholderUtils.formatTimeExpression("week_last_day(yyyyMMdd,0)", date, false));
    }

    // ===== year_week =====

    @Test
    void testYearWeek_withHyphenFormat() {
        // 2026-04-04 是星期六,按周一为首日算在 2026 年第 14 周
        // format 含 - → 输出带分隔符 (DS 行为)
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("year_week(yyyy-MM-dd)", date, false);
        assertEquals("2026-14", result);
    }

    @Test
    void testYearWeek_noHyphenFormat() {
        // format 不含 - → 输出不带分隔符,紧贴拼接(DS 行为)
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("year_week(yyyyMMdd)", date, false);
        assertEquals("202614", result);
    }

    @Test
    void testYearWeek_withDayOfWeek() {
        Date date = createTestDate();
        String result = TimePlaceholderUtils.formatTimeExpression("year_week(yyyy-MM-dd, 7)", date, false);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}"));
    }

    @Test
    void testYearWeek_calendarYearAtBoundary() {
        // 2025-12-29 是 ISO 2026 第 1 周,但 DS 用 Calendar.YEAR 输出 2025 (日历年)
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2025, Calendar.DECEMBER, 29);
        Date date = cal.getTime();
        String result = TimePlaceholderUtils.formatTimeExpression("year_week(yyyy-MM-dd)", date, false);
        assertEquals("2025-01", result);
    }

    @Test
    void testYearWeek_weekBelow10HasLeadingZero() {
        // 1 月初某天,周数应该是 1 位 → 前导 0
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2026, Calendar.JANUARY, 5); // 周一
        Date date = cal.getTime();
        String result = TimePlaceholderUtils.formatTimeExpression("year_week(yyyy-MM-dd)", date, false);
        assertTrue(result.startsWith("2026-0"), "expected 2026-0X for early Jan, got " + result);
    }
}
