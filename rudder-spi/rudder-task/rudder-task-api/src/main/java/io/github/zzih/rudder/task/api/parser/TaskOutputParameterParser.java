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
 * Parses task output parameters from stdout lines, with cross-line buffer support.
 * <p>
 * 兼容 DolphinScheduler 4 种语法(单行 / 跨行均支持):
 * <ul>
 *   <li>{@code ${setValue(key=value)}}</li>
 *   <li>{@code #{setValue(key=value)}}</li>
 * </ul>
 * <p>
 * 使用方式:每收一行 stdout 调一次 {@link #appendParseLog(String)},内部维护一个跨行 buffer,
 * 直到遇到结束标记 {@code )}} 才完成解析。Shell/Python 里跨行 JSON 输出场景:
 * <pre>
 * echo '${setValue(payload={'
 * echo '  "user": "alice",'
 * echo '  "amount": 42'
 * echo '})}'
 * </pre>
 * 上面 4 行会被合并成一个 setValue,key={@code payload},value={@code {...}}。
 * <p>
 * 防 OOM:每个跨行 setValue 限制 {@code maxOneParameterRows} 行 + {@code maxOneParameterLength} 字符。
 * 任一上限触发即丢弃当前 buffer 并 warn,继续扫描后续行。
 * <p>
 * 解析出的 prop 默认 {@code Direct=OUT, type=VARCHAR}。
 *
 * <p><b>线程不安全</b>:每个任务/进程独占一个 parser 实例。
 */
@Slf4j
public class TaskOutputParameterParser {

    private static final String DOLLAR_PREFIX = "${setValue(";
    private static final String HASH_PREFIX = "#{setValue(";
    private static final String SUFFIX = ")}";

    /** 单个 setValue 表达式最多跨多少行 — 防止 {@code ${setValue(} 缺失对应 )}} 时无限堆积。 */
    private final int maxOneParameterRows;

    /** 单个 setValue 表达式总字符数上限 — 防止超长 value 撑爆内存。 */
    private final int maxOneParameterLength;

    /** 用 LinkedHashMap 索引,后写入的同名 prop 覆盖前者(等价 DS 行为)。 */
    private final Map<String, String> rawOutputs = new LinkedHashMap<>();

    /** 跨行 buffer,null = 当前未在解析中。 */
    private List<String> currentTaskOutputParam;
    private long currentTaskOutputParamLength;

    public TaskOutputParameterParser() {
        // 默认 1024 行 / 64KB,够用且单条不至于打爆内存
        this(1024, 64 * 1024);
    }

    public TaskOutputParameterParser(int maxOneParameterRows, int maxOneParameterLength) {
        this.maxOneParameterRows = maxOneParameterRows;
        this.maxOneParameterLength = maxOneParameterLength;
    }

    /**
     * 喂一行 stdout。多次调用累积识别 setValue 表达式(包括跨行场景)。
     */
    public void appendParseLog(String logLine) {
        if (logLine == null) {
            return;
        }

        // ---- 已经在 buffer 模式中:接续上一行未闭合的 setValue
        if (currentTaskOutputParam != null) {
            // 行数 / 长度兜底:任一超限即放弃当前 buffer,继续扫描
            if (currentTaskOutputParam.size() > maxOneParameterRows
                    || currentTaskOutputParamLength > maxOneParameterLength) {
                log.warn("Output param expression too long (rows>{}, length>{}), skipping",
                        maxOneParameterRows, maxOneParameterLength);
                resetBuffer();
                return;
            }

            int closeIdx = logLine.indexOf(SUFFIX);
            if (closeIdx == -1) {
                // 仍未闭合,整行入 buffer,等下一行
                currentTaskOutputParam.add(logLine);
                currentTaskOutputParamLength += logLine.length();
                return;
            }

            // 闭合:把闭合标记之前的内容并入 buffer,产出一条 setValue
            currentTaskOutputParam.add(logLine.substring(0, closeIdx + SUFFIX.length()));
            String fullExpression = String.join("\n", currentTaskOutputParam);
            extract(fullExpression);
            resetBuffer();

            // 同一行剩余部分可能还有 setValue,递归扫
            String tail = logLine.substring(closeIdx + SUFFIX.length());
            if (!tail.isEmpty()) {
                appendParseLog(tail);
            }
            return;
        }

        // ---- 未在 buffer 模式:扫第一个 setValue 起始位置
        int startIdx = firstStartIndex(logLine);
        if (startIdx == -1) {
            return;
        }
        currentTaskOutputParam = new ArrayList<>();
        currentTaskOutputParamLength = 0;
        appendParseLog(logLine.substring(startIdx));
    }

    /** 返回当前已解析的 OUT 参数(全部 {@code Direct.OUT, type=VARCHAR})。 */
    public List<Property> getOutputParams() {
        List<Property> out = new ArrayList<>(rawOutputs.size());
        for (Map.Entry<String, String> e : rawOutputs.entrySet()) {
            out.add(Property.builder()
                    .prop(e.getKey())
                    .direct(Direct.OUT)
                    .type(DataType.VARCHAR)
                    .value(e.getValue())
                    .build());
        }
        return out;
    }

    /** 取 {@code ${setValue(} 或 {@code #{setValue(} 在行内的最早位置,都没有则返回 -1。 */
    private int firstStartIndex(String line) {
        int dollar = line.indexOf(DOLLAR_PREFIX);
        int hash = line.indexOf(HASH_PREFIX);
        if (dollar == -1) {
            return hash;
        }
        if (hash == -1) {
            return dollar;
        }
        return Math.min(dollar, hash);
    }

    /**
     * 从一个完整闭合的 setValue 表达式中提取 key=value。
     * 入参形如 {@code ${setValue(k=v)}} 或 {@code #{setValue(k=v)}};不符合则忽略。
     */
    private void extract(String expression) {
        if (expression == null || !expression.endsWith(SUFFIX)) {
            log.info("Output param malformed, skipping: {}", expression);
            return;
        }
        int prefixLen;
        if (expression.startsWith(DOLLAR_PREFIX)) {
            prefixLen = DOLLAR_PREFIX.length();
        } else if (expression.startsWith(HASH_PREFIX)) {
            prefixLen = HASH_PREFIX.length();
        } else {
            log.info("Output param malformed, skipping: {}", expression);
            return;
        }
        String body = expression.substring(prefixLen, expression.length() - SUFFIX.length());
        int eqIdx = body.indexOf('=');
        if (eqIdx <= 0) {
            log.warn("Output param missing '=', skipping: {}", expression);
            return;
        }
        // 不 trim key — 对齐 DS 行为(`split("=", 2)[0]` 不裁空格),用户写
        // {@code ${setValue( name=v)}} 时 key 是 " name" 不是 "name",跟 DS 一致避免迁移歧义。
        // key.isEmpty() 不可能(上面 eqIdx<=0 已挡掉 body 以 = 开头的场景)
        String key = body.substring(0, eqIdx);
        String value = body.substring(eqIdx + 1);
        if (value.isEmpty()) {
            // 空 value:对齐 DS 行为(允许),但记 warn 提醒用户检查 — 下游 SQL 里 `${k}` → ''
            // 容易让 WHERE name = '' 这种语句静默错误,日志能让排错快一点。
            log.warn("Output param '{}' has empty value: {}", key, expression);
        }
        rawOutputs.put(key, value);
    }

    private void resetBuffer() {
        currentTaskOutputParam = null;
        currentTaskOutputParamLength = 0;
    }
}
