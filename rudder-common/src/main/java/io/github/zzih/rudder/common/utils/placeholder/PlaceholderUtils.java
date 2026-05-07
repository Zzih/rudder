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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder utility for replacing ${varName} style placeholders with values from a parameter map.
 */
public final class PlaceholderUtils {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private PlaceholderUtils() {
    }

    /**
     * Replace all {@code ${varName}} placeholders in the given text with values from the params map.
     *
     * @param text                   the text containing placeholders
     * @param params                 the parameter map (key -> value)
     * @param ignoreUnresolvable     if true, unresolved placeholders are left as-is;
     *                               if false, an {@link IllegalArgumentException} is thrown
     * @return the text with placeholders replaced
     */
    public static String replacePlaceholders(String text, Map<String, String> params, boolean ignoreUnresolvable) {
        if (text == null || text.isEmpty() || params == null || params.isEmpty()) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params.get(key);

            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else if (ignoreUnresolvable) {
                // keep original placeholder
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                throw new IllegalArgumentException("Could not resolve placeholder '${" + key + "}' in: " + text);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
