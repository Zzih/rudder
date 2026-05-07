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

import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-stop parameter resolver that handles both {@code ${varName}} and {@code $[timeExpression]} placeholders.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Replace {@code ${varName}} placeholders using the provided params map</li>
 *   <li>Replace {@code $[timeExpression]} placeholders using the schedule time</li>
 * </ol>
 */
public final class ParameterResolver {

    /**
     * Pattern to match {@code $[...]} time placeholders.
     * Matches {@code $[} followed by any characters except {@code $} and {@code ]}, then {@code ]}.
     */
    private static final Pattern TIME_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\[([^$\\]]+)]");

    private ParameterResolver() {
    }

    /**
     * Resolve all placeholders in the given text.
     *
     * @param text         the text containing placeholders
     * @param params       parameter map for {@code ${varName}} substitution (may be null)
     * @param scheduleTime the base date for {@code $[timeExpression]} substitution (may be null to skip)
     * @return the resolved text
     */
    public static String resolveAll(String text, Map<String, String> params, Date scheduleTime) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Step 1: Replace ${varName} placeholders
        if (params != null && !params.isEmpty()) {
            text = PlaceholderUtils.replacePlaceholders(text, params, true);
        }

        // Step 2: Replace $[timeExpression] placeholders
        if (scheduleTime != null) {
            text = replaceTimePlaceholders(text, scheduleTime);
        }

        return text;
    }

    /**
     * Resolve only {@code $[timeExpression]} placeholders, leaving {@code ${varName}} and {@code !{varName}} for later.
     * <p>
     * SQL 类任务专用:Worker 端先解析时间表达式(确保跟 system.biz.* 一致),
     * {@code ${var}} / {@code !{var}} 留给 SqlExecutor 走 PreparedStatement 路径。
     */
    public static String resolveTimeOnly(String text, Date scheduleTime) {
        if (text == null || text.isEmpty() || scheduleTime == null) {
            return text;
        }
        return replaceTimePlaceholders(text, scheduleTime);
    }

    private static String replaceTimePlaceholders(String text, Date scheduleTime) {
        Matcher matcher = TIME_PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = TimePlaceholderUtils.formatTimeExpression(expression, scheduleTime, true);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
