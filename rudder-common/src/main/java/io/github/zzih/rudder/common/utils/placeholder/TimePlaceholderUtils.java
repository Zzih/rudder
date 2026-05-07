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

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Stack;

/**
 * Utility for formatting time expressions used in {@code $[...]} placeholders.
 * <p>
 * Supported expressions:
 * <ul>
 *   <li>{@code yyyyMMdd} - format the base date</li>
 *   <li>{@code yyyyMMdd+N} / {@code yyyyMMdd-N} - add/subtract N days (N may be an arithmetic expression)</li>
 *   <li>{@code add_months(dateFormat,N)} - add N months</li>
 *   <li>{@code month_begin(dateFormat,N)} - first day of month + N day offset</li>
 *   <li>{@code month_end(dateFormat,N)} - last day of month + N day offset</li>
 *   <li>{@code week_begin(dateFormat,N)} - Monday of current week + N day offset</li>
 *   <li>{@code week_end(dateFormat,N)} - Sunday of current week + N day offset</li>
 *   <li>{@code this_day(dateFormat)} - current day</li>
 *   <li>{@code last_day(dateFormat)} - yesterday</li>
 *   <li>{@code timestamp(dateExpression)} - Unix seconds timestamp of the evaluated expression</li>
 * </ul>
 */
public final class TimePlaceholderUtils {

    private static final String ADD_MONTHS = "add_months";
    private static final String MONTH_BEGIN = "month_begin";
    private static final String MONTH_END = "month_end";
    private static final String WEEK_BEGIN = "week_begin";
    private static final String WEEK_END = "week_end";
    private static final String THIS_DAY = "this_day";
    private static final String LAST_DAY = "last_day";
    private static final String TIMESTAMP = "timestamp";
    // DS 兼容别名:DS 用 month_first_day / month_last_day / week_first_day / week_last_day,
    // Rudder 用 *_begin / *_end。两套语法都接受,从 DS 迁过来的工作流不用改。
    private static final String MONTH_FIRST_DAY = "month_first_day";
    private static final String MONTH_LAST_DAY = "month_last_day";
    private static final String WEEK_FIRST_DAY = "week_first_day";
    private static final String WEEK_LAST_DAY = "week_last_day";
    private static final String YEAR_WEEK = "year_week";

    private TimePlaceholderUtils() {
    }

    /**
     * Format a time expression with the given base date.
     *
     * @param expression     the time expression (content inside {@code $[...]})
     * @param date           the base date (must not be null)
     * @param ignoreInvalid  if true, return the original expression on parse failure;
     *                       if false, throw {@link IllegalArgumentException}
     * @return the formatted date string
     */
    public static String formatTimeExpression(String expression, Date date, boolean ignoreInvalid) {
        if (date == null) {
            throw new IllegalArgumentException("Cannot parse expression '" + expression + "': date is null");
        }
        if (expression == null || expression.isEmpty()) {
            if (ignoreInvalid) {
                return expression;
            }
            throw new IllegalArgumentException("Time expression is null or empty");
        }

        try {
            return doFormat(expression, date);
        } catch (Exception e) {
            if (ignoreInvalid) {
                return expression;
            }
            throw new IllegalArgumentException("Unsupported time expression: " + expression, e);
        }
    }

    private static String doFormat(String expression, Date date) {
        if (expression.startsWith(TIMESTAMP + "(") && expression.endsWith(")")) {
            return handleTimestamp(expression, date);
        }
        if (expression.startsWith(ADD_MONTHS + "(") && expression.endsWith(")")) {
            return handleAddMonths(expression, date);
        }
        if (expression.startsWith(MONTH_BEGIN + "(") && expression.endsWith(")")) {
            return handleMonthBegin(expression, MONTH_BEGIN, date);
        }
        if (expression.startsWith(MONTH_FIRST_DAY + "(") && expression.endsWith(")")) {
            return handleMonthBegin(expression, MONTH_FIRST_DAY, date);
        }
        if (expression.startsWith(MONTH_END + "(") && expression.endsWith(")")) {
            return handleMonthEnd(expression, MONTH_END, date);
        }
        if (expression.startsWith(MONTH_LAST_DAY + "(") && expression.endsWith(")")) {
            return handleMonthEnd(expression, MONTH_LAST_DAY, date);
        }
        if (expression.startsWith(WEEK_BEGIN + "(") && expression.endsWith(")")) {
            return handleWeekBegin(expression, WEEK_BEGIN, date);
        }
        if (expression.startsWith(WEEK_FIRST_DAY + "(") && expression.endsWith(")")) {
            return handleWeekBegin(expression, WEEK_FIRST_DAY, date);
        }
        if (expression.startsWith(WEEK_END + "(") && expression.endsWith(")")) {
            return handleWeekEnd(expression, WEEK_END, date);
        }
        if (expression.startsWith(WEEK_LAST_DAY + "(") && expression.endsWith(")")) {
            return handleWeekEnd(expression, WEEK_LAST_DAY, date);
        }
        if (expression.startsWith(THIS_DAY + "(") && expression.endsWith(")")) {
            return handleThisDay(expression, date);
        }
        if (expression.startsWith(LAST_DAY + "(") && expression.endsWith(")")) {
            return handleLastDay(expression, date);
        }
        if (expression.startsWith(YEAR_WEEK + "(") && expression.endsWith(")")) {
            return handleYearWeek(expression, date);
        }

        // Simple format with optional day offset: yyyyMMdd+N or yyyyMMdd-N
        return handleSimpleFormat(expression, date);
    }

    // ==================== Function handlers ====================

    private static String handleTimestamp(String expression, Date date) {
        String inner = extractFunctionArg(expression, TIMESTAMP);
        String formatted = doFormat(inner, date);
        // Parse the formatted date back and return epoch seconds
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date parsed = sdf.parse(formatted);
            return String.valueOf(parsed.getTime() / 1000);
        } catch (Exception e) {
            // Try other common formats
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                Date parsed = sdf.parse(formatted);
                return String.valueOf(parsed.getTime() / 1000);
            } catch (Exception e2) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date parsed = sdf.parse(formatted);
                    return String.valueOf(parsed.getTime() / 1000);
                } catch (Exception e3) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        Date parsed = sdf.parse(formatted);
                        return String.valueOf(parsed.getTime() / 1000);
                    } catch (Exception e4) {
                        throw new IllegalArgumentException("Cannot parse timestamp from: " + formatted);
                    }
                }
            }
        }
    }

    private static String handleAddMonths(String expression, Date date) {
        String args = extractFunctionArg(expression, ADD_MONTHS);
        int commaIdx = findTopLevelComma(args);
        String dateFormat = args.substring(0, commaIdx);
        String monthExpr = args.substring(commaIdx + 1);
        int months = calculateExpression(monthExpr.trim());

        LocalDate localDate = toLocalDate(date).plusMonths(months);
        return formatLocalDate(localDate, dateFormat);
    }

    private static String handleMonthBegin(String expression, String funcName, Date date) {
        return adjustAndOffsetFormat(expression, funcName, date, TemporalAdjusters.firstDayOfMonth());
    }

    private static String handleMonthEnd(String expression, String funcName, Date date) {
        return adjustAndOffsetFormat(expression, funcName, date, TemporalAdjusters.lastDayOfMonth());
    }

    private static String handleWeekBegin(String expression, String funcName, Date date) {
        return adjustAndOffsetFormat(expression, funcName, date,
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String handleWeekEnd(String expression, String funcName, Date date) {
        return adjustAndOffsetFormat(expression, funcName, date,
                TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * month_begin / month_end / week_begin / week_end 共享形态:
     * 拆 {@code (dateFormat, dayOffsetExpr)} → 应用 {@link TemporalAdjuster} → 加偏移天数 → format。
     */
    private static String adjustAndOffsetFormat(String expression, String funcName, Date date,
                                                java.time.temporal.TemporalAdjuster adjuster) {
        String args = extractFunctionArg(expression, funcName);
        int commaIdx = findTopLevelComma(args);
        String dateFormat = args.substring(0, commaIdx);
        int dayOffset = calculateExpression(args.substring(commaIdx + 1).trim());
        LocalDate result = toLocalDate(date).with(adjuster).plusDays(dayOffset);
        return formatLocalDate(result, dateFormat);
    }

    /**
     * year_week:返回基准日期所在年的第 N 周(对齐 DS {@code calculateYearWeek}):
     * <ul>
     *   <li>年份用 {@link java.util.Calendar#YEAR}(日历年,**不是** ISO weekBasedYear)。例:2025-12-29
     *       是 ISO 2026 第 1 周,但 DS 输出 2025 年的周数</li>
     *   <li>分隔符:format 参数包含 {@code -} 才插入 {@code -},否则紧贴拼接。
     *       {@code year_week(yyyyMMdd)} → {@code 202552};{@code year_week(yyyy-MM-dd)} → {@code 2025-52}</li>
     *   <li>周数 < 10 时前导 0(始终 2 位)</li>
     *   <li>第二个参数 1=周一(默认), 2=周二 ... 7=周日 — 设置每周起始日。最少 4 天构成首周</li>
     * </ul>
     */
    private static String handleYearWeek(String expression, Date date) {
        String args = extractFunctionArg(expression, YEAR_WEEK);
        int commaIdx = findTopLevelComma(args, false);
        String dateFormat = commaIdx >= 0 ? args.substring(0, commaIdx) : args;
        int dowIndex = 1;
        if (commaIdx >= 0) {
            dowIndex = calculateExpression(args.substring(commaIdx + 1).trim());
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setMinimalDaysInFirstWeek(4);
        // dowIndex 1..7 → MONDAY..SUNDAY,越界回退周一
        switch (dowIndex) {
            case 2 -> calendar.setFirstDayOfWeek(java.util.Calendar.TUESDAY);
            case 3 -> calendar.setFirstDayOfWeek(java.util.Calendar.WEDNESDAY);
            case 4 -> calendar.setFirstDayOfWeek(java.util.Calendar.THURSDAY);
            case 5 -> calendar.setFirstDayOfWeek(java.util.Calendar.FRIDAY);
            case 6 -> calendar.setFirstDayOfWeek(java.util.Calendar.SATURDAY);
            case 7 -> calendar.setFirstDayOfWeek(java.util.Calendar.SUNDAY);
            default -> calendar.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        }
        calendar.setTimeInMillis(date.getTime());
        int weekOfYear = calendar.get(java.util.Calendar.WEEK_OF_YEAR);
        int year = calendar.get(java.util.Calendar.YEAR);

        boolean withHyphen = dateFormat != null && dateFormat.contains("-");
        if (withHyphen) {
            return weekOfYear < 10
                    ? String.format("%d-0%d", year, weekOfYear)
                    : String.format("%d-%d", year, weekOfYear);
        }
        return weekOfYear < 10
                ? String.format("%d0%d", year, weekOfYear)
                : String.format("%d%d", year, weekOfYear);
    }

    private static String handleThisDay(String expression, Date date) {
        String dateFormat = extractFunctionArg(expression, THIS_DAY);
        return formatLocalDate(toLocalDate(date), dateFormat);
    }

    private static String handleLastDay(String expression, Date date) {
        String dateFormat = extractFunctionArg(expression, LAST_DAY);
        return formatLocalDate(toLocalDate(date).minusDays(1), dateFormat);
    }

    /**
     * Handle simple format expressions: pure format string, or format+N / format-N for day offsets.
     * The offset N is multiplied by 24*60 minutes (i.e., treated as days), following DolphinScheduler convention.
     */
    private static String handleSimpleFormat(String expression, Date date) {
        // Look for the last '+' or '-' that separates format from offset.
        // We need to be careful: date formats like 'yyyy-MM-dd' contain '-'.
        // Strategy: find the last '+' or '-' where the rest is a valid arithmetic expression (digits and operators).
        int splitIdx = findFormatOffsetSplit(expression);

        if (splitIdx >= 0) {
            String dateFormat = expression.substring(0, splitIdx);
            char op = expression.charAt(splitIdx);
            String offsetExpr = expression.substring(splitIdx + 1);
            int offsetDays = calculateExpression(offsetExpr.trim());
            if (op == '-') {
                offsetDays = -offsetDays;
            }
            LocalDate result = toLocalDate(date).plusDays(offsetDays);
            return formatLocalDate(result, dateFormat);
        }

        // No offset, just format
        return formatLocalDate(toLocalDate(date), expression);
    }

    /**
     * Find the split point between date format and offset expression.
     * Returns index of '+' or '-', or -1 if not found.
     */
    private static int findFormatOffsetSplit(String expression) {
        // Scan from the right to find operator where right side is a valid arithmetic expr
        for (int i = expression.length() - 1; i > 0; i--) {
            char c = expression.charAt(i);
            if ((c == '+' || c == '-') && i > 0) {
                String right = expression.substring(i + 1);
                if (!right.isEmpty() && isArithmeticExpression(right)) {
                    // Make sure the char before is a letter (part of format) not another operator
                    char before = expression.charAt(i - 1);
                    if (Character.isLetter(before) || before == 'd' || before == 'y' || before == 'M'
                            || before == 'H' || before == 'h' || before == 'm' || before == 's' || before == 'S') {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean isArithmeticExpression(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '+' && c != '-' && c != '*' && c != '/' && c != '(' && c != ')'
                    && c != ' ') {
                return false;
            }
        }
        // Must contain at least one digit
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    // ==================== Helper methods ====================

    private static String extractFunctionArg(String expression, String funcName) {
        // e.g., "add_months(yyyyMMdd,-1)" -> "yyyyMMdd,-1"
        return expression.substring(funcName.length() + 1, expression.length() - 1);
    }

    private static int findTopLevelComma(String args) {
        return findTopLevelComma(args, true);
    }

    private static int findTopLevelComma(String args, boolean required) {
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }
        if (required) {
            throw new IllegalArgumentException("No comma found in function arguments: " + args);
        }
        return -1;
    }

    private static LocalDate toLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String formatLocalDate(LocalDate localDate, String format) {
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return new SimpleDateFormat(format).format(date);
    }

    // ==================== Arithmetic expression calculator ====================

    /**
     * Evaluate a simple arithmetic expression supporting +, -, *, / and parentheses.
     * Uses the shunting-yard algorithm to convert to postfix, then evaluates.
     */
    static int calculateExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty arithmetic expression");
        }
        expression = expression.trim();

        // Convert unary +/- to special markers: P (positive) N (negative)
        char[] arr = expression.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == '-') {
                if (i == 0 || isOperatorOrOpenParen(arr[i - 1])) {
                    arr[i] = 'N'; // unary negative
                }
            } else if (arr[i] == '+') {
                if (i == 0 || isOperatorOrOpenParen(arr[i - 1])) {
                    arr[i] = 'P'; // unary positive
                }
            }
        }
        String converted = new String(arr);

        List<String> tokens = tokenize(converted);
        List<String> postfix = toPostfix(tokens);
        return evaluatePostfix(postfix);
    }

    private static boolean isOperatorOrOpenParen(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '(';
    }

    private static List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (!num.isEmpty()) {
                    tokens.add(num.toString());
                    num.setLength(0);
                }
                tokens.add(String.valueOf(c));
            }
        }
        if (!num.isEmpty()) {
            tokens.add(num.toString());
        }
        return tokens;
    }

    private static List<String> toPostfix(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Stack<String> stack = new Stack<>();

        for (String token : tokens) {
            if (token.length() == 1 && !Character.isDigit(token.charAt(0))) {
                char c = token.charAt(0);
                if (c == '(') {
                    stack.push(token);
                } else if (c == ')') {
                    while (!stack.isEmpty() && !"(".equals(stack.peek())) {
                        output.add(stack.pop());
                    }
                    if (!stack.isEmpty()) {
                        stack.pop(); // remove '('
                    }
                } else {
                    // operator
                    while (!stack.isEmpty() && precedence(stack.peek()) >= precedence(token)
                            && !"(".equals(stack.peek())) {
                        output.add(stack.pop());
                    }
                    stack.push(token);
                }
            } else {
                output.add(token); // number
            }
        }
        while (!stack.isEmpty()) {
            output.add(stack.pop());
        }
        return output;
    }

    private static int precedence(String op) {
        if (op.length() != 1) {
            return -1;
        }
        char c = op.charAt(0);
        if (c == 'P' || c == 'N') {
            return 3; // unary
        }
        if (c == '*' || c == '/') {
            return 2;
        }
        if (c == '+' || c == '-') {
            return 1;
        }
        return -1;
    }

    private static int evaluatePostfix(List<String> postfix) {
        Stack<Integer> stack = new Stack<>();
        for (String token : postfix) {
            if (token.length() == 1 && !Character.isDigit(token.charAt(0))) {
                char op = token.charAt(0);
                if (op == 'P' || op == 'N') {
                    // Unary operator
                    int operand = stack.pop();
                    stack.push(op == 'N' ? -operand : operand);
                } else {
                    int b = stack.pop();
                    int a = stack.pop();
                    switch (op) {
                        case '+' -> stack.push(a + b);
                        case '-' -> stack.push(a - b);
                        case '*' -> stack.push(a * b);
                        case '/' -> stack.push(a / b);
                        default -> throw new IllegalArgumentException("Unknown operator: " + op);
                    }
                }
            } else {
                stack.push(Integer.parseInt(token));
            }
        }
        return stack.pop();
    }
}
