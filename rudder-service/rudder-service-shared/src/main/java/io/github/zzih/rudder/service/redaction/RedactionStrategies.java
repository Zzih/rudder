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

package io.github.zzih.rudder.service.redaction;

import io.github.zzih.rudder.common.enums.redaction.RedactionExecutorType;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.dao.entity.RedactionStrategyEntity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.extern.slf4j.Slf4j;

/**
 * 策略执行器 —— 把 {@link RedactionStrategyEntity} 配置应用到具体值上。
 */
@Slf4j
public final class RedactionStrategies {

    /** REGEX_REPLACE 策略的编译后 Pattern 缓存,避免每行每列重新编译。 */
    private static final ConcurrentHashMap<String, Pattern> COMPILED_REGEX = new ConcurrentHashMap<>();

    /** 失败标记,插入 COMPILED_REGEX 让后续直接跳过。 */
    private static final Pattern INVALID_REGEX = Pattern.compile("$^");

    private RedactionStrategies() {
    }

    /** 对结构化值应用策略。null 输入返回 null。 */
    public static Object applyValue(RedactionStrategyEntity strategy, Object value) {
        if (strategy == null || strategy.getExecutorType() == null) {
            return value;
        }
        if (strategy.getExecutorType() == RedactionExecutorType.REMOVE) {
            return null;
        }
        if (value == null) {
            return null;
        }
        return applyToString(strategy, value.toString());
    }

    /**
     * 对文本中命中子串应用策略。TEXT 规则的 {@code rulePattern} 负责找 PII 片段,
     * 命中的子串再按策略替换。{@code REMOVE} 在自由文本里的语义是"删掉命中部分"。
     */
    public static String scrubText(Pattern rulePattern, RedactionStrategyEntity strategy, String text) {
        if (rulePattern == null || text == null || text.isEmpty()
                || strategy == null || strategy.getExecutorType() == null) {
            return text;
        }
        Matcher m = rulePattern.matcher(text);
        return m.replaceAll(mr -> {
            String hit = mr.group();
            String replaced = strategy.getExecutorType() == RedactionExecutorType.REMOVE
                    ? ""
                    : applyToString(strategy, hit);
            return Matcher.quoteReplacement(replaced == null ? "" : replaced);
        });
    }

    /** 按 executorType 应用策略,返回字符串结果。不处理 REMOVE(调用方按场景决定语义)。 */
    private static String applyToString(RedactionStrategyEntity strategy, String s) {
        return switch (strategy.getExecutorType()) {
            case REGEX_REPLACE -> applyRegexReplace(strategy, s);
            case PARTIAL -> applyPartial(strategy, s);
            case REPLACE -> strategy.getReplaceValue() != null ? strategy.getReplaceValue() : "***";
            case HASH -> applyHash(strategy, s);
            case REMOVE -> s; // 不应到这,applyValue/scrubText 已处理
        };
    }

    private static String applyRegexReplace(RedactionStrategyEntity strategy, String s) {
        String regex = strategy.getMatchRegex();
        String tmpl = strategy.getReplacement();
        if (regex == null || regex.isEmpty() || tmpl == null) {
            return s;
        }
        Pattern p = COMPILED_REGEX.computeIfAbsent(regex, r -> {
            try {
                return Pattern.compile(r);
            } catch (PatternSyntaxException e) {
                log.warn("redaction strategy {} has invalid regex: {}", strategy.getCode(), e.getMessage());
                return INVALID_REGEX;
            }
        });
        if (p == INVALID_REGEX) {
            return s;
        }
        return p.matcher(s).replaceAll(tmpl);
    }

    private static String applyPartial(RedactionStrategyEntity strategy, String s) {
        int head = strategy.getKeepPrefix() != null ? Math.max(0, strategy.getKeepPrefix()) : 0;
        int tail = strategy.getKeepSuffix() != null ? Math.max(0, strategy.getKeepSuffix()) : 0;
        String mask = strategy.getMaskChar() != null && !strategy.getMaskChar().isEmpty()
                ? strategy.getMaskChar()
                : "*";
        int len = s.length();
        if (head + tail >= len) {
            return mask.repeat(len);
        }
        int maskCount = len - head - tail;
        return s.substring(0, head) + mask.repeat(maskCount) + s.substring(len - tail);
    }

    private static String applyHash(RedactionStrategyEntity strategy, String s) {
        int len = strategy.getHashLength() != null ? Math.max(1, strategy.getHashLength()) : 8;
        String hex = CryptoUtils.sha256Hex(s);
        return hex.length() > len ? hex.substring(0, len) : hex;
    }
}
