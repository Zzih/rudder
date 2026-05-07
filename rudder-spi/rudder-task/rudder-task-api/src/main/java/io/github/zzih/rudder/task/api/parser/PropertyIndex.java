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

package io.github.zzih.rudder.task.api.parser;

import io.github.zzih.rudder.common.param.Property;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@code List<Property>} 投影成 prop 索引的 Map 视图。
 * <p>
 * SqlPreprocessor 拿 prop→Property 视图查 {@code ${var}} 的类型;
 * VarPoolFilter 拿 prop→value 视图(只取非 null value)做白名单过滤。
 * 都用 LinkedHashMap 保留插入顺序便于审计,prop 重复时**后写入覆盖前者**(对齐 DS varPool merge 行为)。
 */
public final class PropertyIndex {

    private PropertyIndex() {
    }

    /** prop → Property,prop=null 的元素跳过。 */
    public static Map<String, Property> byProp(List<Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, Property> m = new LinkedHashMap<>(properties.size());
        for (Property p : properties) {
            if (p != null && p.getProp() != null) {
                m.put(p.getProp(), p);
            }
        }
        return m;
    }

    /** prop → value(只取非 null value),给占位符替换 / 白名单 lookup 用。 */
    public static Map<String, String> byPropToValue(List<Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> m = new LinkedHashMap<>(properties.size());
        for (Property p : properties) {
            if (p != null && p.getProp() != null) {
                m.put(p.getProp(), p.getValue());
            }
        }
        return m;
    }
}
