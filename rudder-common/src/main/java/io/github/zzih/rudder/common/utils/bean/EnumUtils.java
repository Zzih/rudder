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

package io.github.zzih.rudder.common.utils.bean;

import java.util.Optional;

/** Enum 通用工具。 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * 大小写不敏感地按名字查 enum 值,空白 / null 返回 empty。
     * <p>
     * 调用方按需选择宽松或严格语义:
     * <ul>
     *   <li>宽松(找不到走 default):{@code lookupByName(...).orElse(null)}</li>
     *   <li>严格(找不到抛错):{@code lookupByName(...).orElseThrow(...)}</li>
     * </ul>
     */
    public static <E extends Enum<E>> Optional<E> lookupByName(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        for (E v : type.getEnumConstants()) {
            if (v.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }
}
