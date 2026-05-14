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

package io.github.zzih.rudder.common.exception;

/** 异常摘要工具:把 cause chain 拼成适合存 DB / 展示给用户的单行字符串。 */
public final class ExceptionFormatter {

    /** 最多展开 5 层 cause,再深的丢弃,避免 wrapper 嵌套过深刷屏。 */
    private static final int MAX_DEPTH = 5;

    /** 拼接结果上限,超过截断;DB error_message 一般 TEXT 列,1KB 足够带根因。 */
    private static final int MAX_LENGTH = 1000;

    private static final String CAUSED_BY = " | caused by: ";

    private static final String TRUNCATED = "...";

    private ExceptionFormatter() {
    }

    /**
     * 把异常 cause chain 摘要成单行字符串。仅取每层的 simpleName + message,不带 stack trace
     * (stack trace 进 log,不进 DB)。
     */
    public static String summarize(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        int depth = 0;
        while (t != null && depth < MAX_DEPTH) {
            if (depth > 0) {
                sb.append(CAUSED_BY);
            }
            sb.append(t.getClass().getSimpleName());
            String msg = messageOf(t);
            if (msg != null && !msg.isBlank()) {
                sb.append(": ").append(msg);
            }
            t = t.getCause();
            depth++;
        }
        if (sb.length() > MAX_LENGTH) {
            return sb.substring(0, MAX_LENGTH) + TRUNCATED;
        }
        return sb.toString();
    }

    private static String messageOf(Throwable t) {
        return t instanceof RudderException re ? re.resolvedMessage() : t.getMessage();
    }
}
