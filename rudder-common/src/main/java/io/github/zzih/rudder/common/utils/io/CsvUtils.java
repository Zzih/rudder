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

package io.github.zzih.rudder.common.utils.io;

public final class CsvUtils {

    private CsvUtils() {
    }

    /**
     * RFC 4180 escape:含 {@code , " \r \n} 时用双引号包裹,内部 {@code "} 转义为 {@code ""}。
     * null 视作空串。
     */
    public static String escape(String v) {
        if (v == null) {
            return "";
        }
        boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!needsQuote) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
