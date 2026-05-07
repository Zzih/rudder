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

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.param.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * dealOutParam 模式的白名单过滤器。对齐 DolphinScheduler {@code AbstractParameters.dealOutParam}:
 * 只有 task_definition 显式声明了 {@code Direct.OUT} 的 prop 才能进 varPool,其他一律丢弃。
 *
 * <p>语义(对齐 DS {@code AbstractParameters.dealOutParam}):
 * <ul>
 *   <li>raw value 来源 = Shell/Python stdout 的 {@code ${setValue(k=v)}} 解析,或 SQL firstRow 列值</li>
 *   <li>spec = task_definition.output_params 中 {@code Direct.OUT} 的 prop 列表(声明 + 类型 + 默认值)</li>
 *   <li>命中 spec 的 prop:用 raw value 填进对应 spec 的 type/direct,产出 final Property</li>
 *   <li>没命中 spec 的 prop:丢弃</li>
 *   <li>spec 中声明了但 raw 里没值的 prop:**保留声明里的 default value 产出**
 *       (DS 行为 — 用户可以靠这个给 OUT 兜底默认值,即使 task 没明确产出)</li>
 *   <li>spec 默认值也为 null:仍保留 Property(value=null)— 对齐 DS,让下游 {@code ${prop}}
 *       能命中索引(避免静默落成字面量)</li>
 * </ul>
 */
@Slf4j
public final class VarPoolFilter {

    private VarPoolFilter() {
    }

    /**
     * 按 spec 白名单过滤 Shell/Python 解析出的原始 raw map。返回的 Property 全部 {@code Direct.OUT}。
     *
     * @param raw  Shell stdout 解析出的 prop→value 候选(无 type/direct 信息)
     * @param spec task_definition.output_params 解出的 spec 列表
     */
    public static List<Property> filterByMap(List<Property> raw, List<Property> spec) {
        Map<String, String> rawByName = PropertyIndex.byPropToValue(raw);
        return filter(rawByName, spec);
    }

    /**
     * 按 spec 白名单过滤一个 row(SQL firstRow 列值)。row 的 key 必须跟 spec 中 prop 名一致才命中。
     *
     * @param row  SQL 结果集首行 column → value;value 已 toString
     * @param spec task_definition.output_params 解出的 spec 列表
     */
    public static List<Property> filterByRow(Map<String, ?> row, List<Property> spec) {
        if (row == null || row.isEmpty()) {
            return filter(Map.of(), spec);
        }
        Map<String, String> stringRow = new LinkedHashMap<>(row.size());
        for (Map.Entry<String, ?> e : row.entrySet()) {
            stringRow.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
        }
        return filter(stringRow, spec);
    }

    private static List<Property> filter(Map<String, String> rawByName, List<Property> spec) {
        if (spec == null || spec.isEmpty()) {
            return List.of();
        }
        List<Property> out = new ArrayList<>();
        for (Property declared : spec) {
            if (declared == null || declared.getProp() == null) {
                continue;
            }
            if (declared.getDirect() != Direct.OUT) {
                continue;
            }
            // raw 命中:用 task 实际产出值;raw 未命中:回退到 spec 声明的 default value。
            // 两者都为 null → **仍保留 Property(value=null)** — 对齐 DS AbstractParameters.dealOutParam,
            // 它把所有声明 OUT 一律放进 varPool,让下游 ${prop} 至少能解析(否则会拿到字面量)。
            String value = rawByName.containsKey(declared.getProp())
                    ? rawByName.get(declared.getProp())
                    : declared.getValue();
            out.add(Property.builder()
                    .prop(declared.getProp())
                    .direct(Direct.OUT)
                    .type(declared.getType() != null ? declared.getType() : DataType.VARCHAR)
                    .value(value)
                    .build());
        }
        return out;
    }

}
