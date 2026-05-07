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

import io.github.zzih.rudder.common.enums.redaction.RedactionRuleType;
import io.github.zzih.rudder.dao.dao.RedactionRuleDao;
import io.github.zzih.rudder.dao.dao.RedactionStrategyDao;
import io.github.zzih.rudder.dao.entity.RedactionRuleEntity;
import io.github.zzih.rudder.dao.entity.RedactionStrategyEntity;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 脱敏规则 + 策略的预编译快照。缓存走 {@link GlobalCacheService}：本地 Caffeine 命中，
 * miss 时从 DB 重建并预编译 Pattern；admin CUD 调 {@link #invalidate()} 触发跨节点失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedactionRuleCache {

    private final GlobalCacheService cache;
    private final RedactionRuleDao ruleDao;
    private final RedactionStrategyDao strategyDao;

    public Snapshot get() {
        return cache.getOrLoad(GlobalCacheKey.REDACTION, this::build);
    }

    public void invalidate() {
        cache.invalidate(GlobalCacheKey.REDACTION);
    }

    private Snapshot build() {
        List<RedactionRuleEntity> rules = ruleDao.selectAllEnabled();
        List<RedactionStrategyEntity> strategies = strategyDao.selectAllEnabled();
        if (rules == null) {
            rules = List.of();
        }
        if (strategies == null) {
            strategies = List.of();
        }

        Map<String, RedactionStrategyEntity> strategyByCode = strategies.stream()
                .filter(s -> s.getCode() != null)
                .collect(Collectors.toMap(RedactionStrategyEntity::getCode, s -> s, (a, b) -> a));

        List<CompiledRule> tagRules = new ArrayList<>();
        List<CompiledRule> columnRules = new ArrayList<>();
        List<CompiledRule> textRules = new ArrayList<>();

        for (RedactionRuleEntity rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled()) || rule.getType() == null) {
                continue;
            }
            RedactionStrategyEntity strategy = strategyByCode.get(rule.getStrategyCode());
            if (strategy == null || !Boolean.TRUE.equals(strategy.getEnabled())) {
                continue;
            }
            Pattern compiled = compilePattern(rule);
            if (compiled == null && rule.getType() != RedactionRuleType.TAG) {
                continue;
            }
            CompiledRule cr = new CompiledRule(rule, strategy, compiled);
            switch (rule.getType()) {
                case TAG -> tagRules.add(cr);
                case COLUMN -> columnRules.add(cr);
                case TEXT -> textRules.add(cr);
            }
        }

        Comparator<CompiledRule> byPriority =
                Comparator.comparingInt(c -> c.rule.getPriority() == null ? 100 : c.rule.getPriority());
        tagRules.sort(byPriority);
        columnRules.sort(byPriority);
        textRules.sort(byPriority);

        return new Snapshot(List.copyOf(tagRules), List.copyOf(columnRules), List.copyOf(textRules));
    }

    private static Pattern compilePattern(RedactionRuleEntity rule) {
        String pat = rule.getPattern();
        if (pat == null || pat.isEmpty()) {
            return null;
        }
        try {
            if (rule.getType() == RedactionRuleType.COLUMN) {
                return Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
            }
            return Pattern.compile(pat);
        } catch (PatternSyntaxException e) {
            log.warn("redaction rule {} has invalid regex, skipped: {}", rule.getName(), e.getMessage());
            return null;
        }
    }

    /** 预编译后的单条规则：原始规则 + 其引用的策略 + 编译好的 Pattern。 */
    @Getter
    @AllArgsConstructor
    public static class CompiledRule {

        private final RedactionRuleEntity rule;
        private final RedactionStrategyEntity strategy;
        /** TAG 类型可能为 null（精确 tag 不需 regex）；TEXT/COLUMN 非 null。 */
        private final Pattern pattern;
    }

    /** 三类规则的分桶快照，已按 priority 排序。 */
    @Getter
    public static class Snapshot {

        private final List<CompiledRule> tagRules;
        private final List<CompiledRule> columnRules;
        private final List<CompiledRule> textRules;

        public Snapshot(List<CompiledRule> tagRules, List<CompiledRule> columnRules, List<CompiledRule> textRules) {
            this.tagRules = tagRules;
            this.columnRules = columnRules;
            this.textRules = textRules;
        }

        public boolean isEmpty() {
            return tagRules.isEmpty() && columnRules.isEmpty() && textRules.isEmpty();
        }
    }
}
