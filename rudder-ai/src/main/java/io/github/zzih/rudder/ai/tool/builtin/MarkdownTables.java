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

package io.github.zzih.rudder.ai.tool.builtin;

import java.util.List;

/** 工具返回结果渲染成 markdown 表格的共享实现。 */
final class MarkdownTables {

    private static final int MAX_CELL_CHARS = 120;

    private MarkdownTables() {
    }

    static String render(List<String> columns, List<Object[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(" --- |");
        }
        sb.append("\n");
        for (Object[] row : rows) {
            sb.append("|");
            for (Object v : row) {
                sb.append(" ").append(cell(v)).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String cell(Object v) {
        if (v == null) {
            return "NULL";
        }
        String s = v.toString();
        if (s.length() > MAX_CELL_CHARS) {
            s = s.substring(0, MAX_CELL_CHARS) + "…";
        }
        return s.replace("\n", " ").replace("|", "\\|");
    }
}
