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

package io.github.zzih.rudder.task.api.task.executor;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

/**
 * SQL 占位符预处理:把用户原文 SQL + paramMap 转换成 PreparedStatement 可执行的形态。
 *
 * <p>处理顺序(对齐 DolphinScheduler):
 * <ol>
 *   <li>{@code !{var}} → 原值字符串替换。给 JDBC 不能 prepared 的位置(表名 / 列名 / DDL 关键字 /
 *       动态 ORDER BY)用。直接拼进 SQL,**不走** PreparedStatement。</li>
 *   <li>{@code ${var}} → {@code ?}。每出现一次按顺序追加一条 {@link Property} 到 binds 列表。
 *       LIST 类型的 {@code ${ids}}(value 是 JSON 数组字符串)自动展开成 {@code ?,?,?},
 *       binds 里追加 N 条 INTEGER/VARCHAR Property。</li>
 *   <li>未在 paramMap 里的 {@code ${var}} 保留原样(由调用方决定 fail-fast 还是放行)。</li>
 * </ol>
 *
 * <p>不处理 {@code $[time]} 时间表达式 — 在 Task / Worker 层面已先解析掉。
 */
@Slf4j
public final class SqlPreprocessor {

    /**
     * {@code !{var}} 原值替换。前后可选单/双引号被一起吃掉(DS 行为):
     * {@code '!{tbl}'} 整体替换成 {@code users},不留单引号。表名场景常见。
     * <p>
     * 已知边界:不平衡引号(如 {@code '!{tbl}} 缺右引号)会吞掉左引号,产出非法 SQL 让 driver 报错。
     */
    private static final Pattern RAW_PATTERN = Pattern.compile("['\"]?!\\{([^}]+)}['\"]?");

    /**
     * {@code ${var}} 占位符。前后可选单/双引号被一起吃掉(对齐 DS {@code SQL_PARAMS_REGEX}):
     * {@code WHERE name = '${user}'} → {@code WHERE name = ?},JDBC setString 正确加引号。
     * 不这样做的话,{@code '${user}'} → {@code '?'} 会让 {@code ?} 落进字面量字符串,
     * bind 槽位错位 + 参数索引错乱。
     * <p>
     * 已知边界:不平衡引号(如 {@code '${user}} 缺右引号)与 DS 行为不完全一致 — Rudder 吞左引号,
     * DS 保留。两者都产出非法 SQL,差异仅影响错误信息行号。
     */
    private static final Pattern VAR_PATTERN = Pattern.compile("['\"]?\\$\\{([^}]+)}['\"]?");

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private SqlPreprocessor() {
    }

    /**
     * 单条 SQL 经过两遍替换的结果。
     *
     * @param sql   占位符替换后的 SQL,所有 {@code ${var}} 已变成 {@code ?},{@code !{var}} 已字符串拼接
     * @param binds 与 {@code ?} 顺序对应的绑定参数列表(空 SQL 也返回空列表)
     */
    public record Prepared(String sql, List<Property> binds) {
    }

    /**
     * 对单条 SQL 做 !{} → ${} → ? 三步预处理。
     *
     * @param sql      原始 SQL(已剥离 {@code $[time]})
     * @param paramMap prop → Property,prop 在两种语法下都按这个 map 查
     */
    public static Prepared preprocess(String sql, Map<String, Property> paramMap) {
        if (sql == null || sql.isEmpty()) {
            return new Prepared(sql, List.of());
        }
        Map<String, Property> params = paramMap != null ? paramMap : Map.of();

        // 第 1 遍:!{var} → 原值字符串替换。先做这步是因为表名/列名换上后 SQL 才完整,
        // 避免后续 ${var} 命中表名位置导致 SQL 语法错。
        String afterRaw = replaceRawValues(sql, params);

        // 第 2 遍:${var} → ?,按出现顺序产 binds 列表。LIST 类型自动展开。
        return replacePreparedVars(afterRaw, params);
    }

    private static String replaceRawValues(String sql, Map<String, Property> params) {
        Matcher m = RAW_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).trim();
            Property p = params.get(name);
            if (p == null || p.getValue() == null) {
                // 未解析:保留原样,让调用方看到 !{var} 提示问题(对齐 DS 的 ignoreUnresolvable=true)
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            } else {
                // !{var} 是字符串拼接,**绕过** PreparedStatement 防注入。如果 value 来源是
                // 用户控制的 runtime/上游 OUT,就有 SQL 注入风险。表名 / 列名 / DDL 关键字这类
                // JDBC 不能 prepared 的位置才该用 !{},别接来路不明的输入。这里 warn 让审计能扫到。
                log.warn("Raw substitution !{{}} = \"{}\" — value bypasses PreparedStatement; "
                        + "ensure the source is trusted (project/global/local IN), not user runtime input",
                        name, p.getValue());
                m.appendReplacement(sb, Matcher.quoteReplacement(p.getValue()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Prepared replacePreparedVars(String sql, Map<String, Property> params) {
        Matcher m = VAR_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        List<Property> binds = new ArrayList<>();

        while (m.find()) {
            String name = m.group(1).trim();
            Property p = params.get(name);

            if (p == null || p.getValue() == null) {
                // 未解析:保留原样。后续 PreparedStatement.execute 时若 SQL 里残留 ${...} 会被驱动报错,
                // 比起静默吞掉用 NULL 更安全。
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }

            // LIST 类型展开:value 形如 "[1,2,3]" 或 "[\"a\",\"b\"]"
            if (p.getType() == DataType.LIST) {
                List<Object> list = parseList(p.getValue());
                if (list.isEmpty()) {
                    // 空 LIST → 用 NULL 占位,避免 SQL 语法错(IN ()是非法的)
                    m.appendReplacement(sb, "NULL");
                    continue;
                }
                StringBuilder placeholders = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    placeholders.append(i == 0 ? "?" : ",?");
                    Object v = list.get(i);
                    binds.add(Property.builder()
                            .prop(p.getProp())
                            .direct(p.getDirect())
                            .type(inferElementType(v))
                            .value(v == null ? null : v.toString())
                            .build());
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(placeholders.toString()));
            } else {
                m.appendReplacement(sb, "?");
                binds.add(p);
            }
        }
        m.appendTail(sb);
        return new Prepared(sb.toString(), binds);
    }

    private static List<Object> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.fromJson(json, LIST_TYPE);
        } catch (Exception e) {
            // value 不是合法 JSON 数组 → 退化为单元素列表(把原值当成一个 String)。
            // 用户声明 LIST 但传了 "42" 这种标量,这里就只展开成一个 ?,跟用户预期可能不符 —
            // warn 一下让排错时能看到。
            log.warn("LIST value is not a JSON array, falling back to single-element list: {}",
                    json.length() > 200 ? json.substring(0, 200) + "..." : json);
            return List.of(json);
        }
    }

    /** LIST 元素类型推断:Integer/Long/Double/Boolean/String,匹配 JDBC 强类型 setXxx。 */
    private static DataType inferElementType(Object v) {
        if (v instanceof Integer) {
            return DataType.INTEGER;
        }
        if (v instanceof Long) {
            return DataType.LONG;
        }
        if (v instanceof Float) {
            return DataType.FLOAT;
        }
        if (v instanceof Double) {
            return DataType.DOUBLE;
        }
        if (v instanceof Boolean) {
            return DataType.BOOLEAN;
        }
        return DataType.VARCHAR;
    }

    /** 从 List&lt;Property&gt; 构建 prop→Property 的查找视图,prop 重复时后写入覆盖前者。 */
    public static Map<String, Property> indexByProp(List<Property> properties) {
        return io.github.zzih.rudder.task.api.parser.PropertyIndex.byProp(properties);
    }
}
