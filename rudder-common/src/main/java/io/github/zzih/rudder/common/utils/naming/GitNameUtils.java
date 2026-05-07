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

package io.github.zzih.rudder.common.utils.naming;

public final class GitNameUtils {

    private GitNameUtils() {
    }

    /**
     * 把任意名称转为 Gitea 允许的 org/repo 名（字母数字、`-`、`_`、`.`，且首字符必须是字母数字）。
     * 空值 / 全非法字符返回 {@code "default"}；首字符非字母数字时前缀 {@code "r"}。
     */
    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "default";
        }
        String cleaned = name.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        if (!Character.isLetterOrDigit(cleaned.charAt(0))) {
            cleaned = "r" + cleaned;
        }
        return cleaned;
    }
}
