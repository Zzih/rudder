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

import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.dao.entity.RedactionStrategyEntity;
import io.github.zzih.rudder.service.redaction.RedactionRuleCache.CompiledRule;
import io.github.zzih.rudder.service.redaction.RedactionRuleCache.Snapshot;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 平台级脱敏实现。三类规则按 TAG → COLUMN → TEXT 优先级短路,每个 cell 最多脱一次。
 */
@Slf4j
@Service
public class LocalRedactionService implements RedactionService {

    private final RedactionRuleCache ruleCache;
    private final ColumnTagResolver tagResolver;

    public LocalRedactionService(RedactionRuleCache ruleCache, ObjectProvider<ColumnTagResolver> tagResolverProvider) {
        this.ruleCache = ruleCache;
        this.tagResolver = tagResolverProvider.getIfAvailable(() -> ColumnTagResolver.EMPTY);
    }

    @Override
    public Object applyValue(ColumnMeta column, Object value) {
        if (value == null || column == null) {
            return value;
        }
        Snapshot snap = ruleCache.get();
        if (snap.isEmpty()) {
            return value;
        }
        return applyValueWithSnapshot(column, value, snap);
    }

    @Override
    public List<Map<String, Object>> applyMapRows(List<ColumnMeta> columns, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || columns == null || columns.isEmpty()) {
            return rows;
        }
        Snapshot snap = ruleCache.get();
        if (snap.isEmpty()) {
            return rows;
        }
        // 每次 applyMapRows 先对 columns 做一次 dispatch 预计算:每列对应"TAG 命中的策略" /
        // "COLUMN 命中的策略" / "走 TEXT 规则" / "原样"。避免每行每列重复遍历规则列表和 mutate ColumnMeta。
        ColumnPlan[] plan = buildPlan(columns, snap);
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < plan.length; i++) {
                ColumnPlan p = plan[i];
                if (p == null || p.action == Action.NOOP) {
                    continue;
                }
                Object val = row.get(p.colName);
                if (val == null) {
                    continue;
                }
                Object out = switch (p.action) {
                    case APPLY -> RedactionStrategies.applyValue(p.strategy, val);
                    case TEXT_ONLY -> val instanceof String s ? applyTextRules(s, snap.getTextRules()) : val;
                    case NOOP -> val;
                };
                row.put(p.colName, out);
            }
        }
        return rows;
    }

    /** 对一个 cell 应用三类规则(applyValue 路径 —— 单 cell 慢路径,不做 plan 预计算)。 */
    private Object applyValueWithSnapshot(ColumnMeta column, Object value, Snapshot snap) {
        if (value == null) {
            return null;
        }
        ColumnPlan p = planForColumn(column, snap);
        return switch (p.action) {
            case APPLY -> RedactionStrategies.applyValue(p.strategy, value);
            case TEXT_ONLY -> value instanceof String s ? applyTextRules(s, snap.getTextRules()) : value;
            case NOOP -> value;
        };
    }

    private ColumnPlan[] buildPlan(List<ColumnMeta> columns, Snapshot snap) {
        ColumnPlan[] plan = new ColumnPlan[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            ColumnMeta col = columns.get(i);
            if (col == null || col.getName() == null) {
                continue;
            }
            plan[i] = planForColumn(col, snap);
        }
        return plan;
    }

    /** TAG → COLUMN → TEXT 优先级短路,决定该列的处理动作。不 mutate ColumnMeta。 */
    private ColumnPlan planForColumn(ColumnMeta column, Snapshot snap) {
        String colName = column.getName();
        // 1. TAG:优先用 ColumnMeta 自带;否则按 originalTable/originalColumn 去元数据平台查
        if (!snap.getTagRules().isEmpty()) {
            List<String> tags = column.getTags();
            if ((tags == null || tags.isEmpty())
                    && column.getOriginalColumn() != null && column.getOriginalTable() != null) {
                tags = tagResolver.getTags(
                        column.getDatasourceName(), column.getDatabase(),
                        column.getOriginalTable(), column.getOriginalColumn());
            }
            if (tags != null && !tags.isEmpty()) {
                CompiledRule tagRule = findTagRule(tags, snap.getTagRules());
                if (tagRule != null) {
                    return new ColumnPlan(colName, Action.APPLY, tagRule.getStrategy());
                }
            }
        }
        // 2. COLUMN(优先按 originalColumn,没有则用结果列名)
        String lookupCol = column.getOriginalColumn() != null ? column.getOriginalColumn() : colName;
        if (lookupCol != null) {
            CompiledRule colRule = findColumnRule(lookupCol, snap.getColumnRules());
            if (colRule != null) {
                return new ColumnPlan(colName, Action.APPLY, colRule.getStrategy());
            }
        }
        // 3. 兜底字符串内容扫描
        if (!snap.getTextRules().isEmpty()) {
            return new ColumnPlan(colName, Action.TEXT_ONLY, null);
        }
        return new ColumnPlan(colName, Action.NOOP, null);
    }

    private enum Action {
        APPLY, TEXT_ONLY, NOOP
    }

    private record ColumnPlan(String colName, Action action, RedactionStrategyEntity strategy) {
    }

    /** TAG 规则匹配:rule.pattern 可以是精确 tag / 前缀通配 / 正则。 */
    private static CompiledRule findTagRule(List<String> columnTags, List<CompiledRule> tagRules) {
        for (CompiledRule cr : tagRules) {
            String pat = cr.getRule().getPattern();
            if (pat == null) {
                continue;
            }
            for (String tag : columnTags) {
                if (tag == null) {
                    continue;
                }
                if (tagMatches(pat, cr.getPattern(), tag)) {
                    return cr;
                }
            }
        }
        return null;
    }

    /** 支持三种语法:精确("PII.Phone") / 前缀通配("PII.*") / 正则("/regex/")。 */
    private static boolean tagMatches(String rulePattern, Pattern compiled, String tag) {
        if (rulePattern.startsWith("/") && rulePattern.endsWith("/") && compiled != null) {
            return compiled.matcher(tag).matches();
        }
        if (rulePattern.endsWith(".*")) {
            return tag.startsWith(rulePattern.substring(0, rulePattern.length() - 2));
        }
        return rulePattern.equals(tag);
    }

    private static CompiledRule findColumnRule(String columnName, List<CompiledRule> columnRules) {
        for (CompiledRule cr : columnRules) {
            Pattern p = cr.getPattern();
            if (p != null && p.matcher(columnName).matches()) {
                return cr;
            }
        }
        return null;
    }

    @Override
    public String scrubText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Snapshot snap = ruleCache.get();
        if (snap.getTextRules().isEmpty()) {
            return text;
        }
        return applyTextRules(text, snap.getTextRules());
    }

    private static String applyTextRules(String text, List<CompiledRule> textRules) {
        String s = text;
        for (CompiledRule cr : textRules) {
            s = RedactionStrategies.scrubText(cr.getPattern(), cr.getStrategy(), s);
        }
        return s;
    }

    @Override
    public void refreshRules() {
        ruleCache.invalidate();
    }

    @Override
    public String testApply(StrategySpec spec, String rulePattern, String sample) {
        if (sample == null || spec == null) {
            return sample;
        }
        RedactionStrategyEntity strategy = toEntity(spec);
        if (rulePattern != null && !rulePattern.isEmpty()) {
            try {
                Pattern p = Pattern.compile(rulePattern);
                return RedactionStrategies.scrubText(p, strategy, sample);
            } catch (java.util.regex.PatternSyntaxException e) {
                return "<invalid regex: " + e.getMessage() + ">";
            }
        }
        Object out = RedactionStrategies.applyValue(strategy, sample);
        return out == null ? null : out.toString();
    }

    private static RedactionStrategyEntity toEntity(StrategySpec s) {
        RedactionStrategyEntity e = new RedactionStrategyEntity();
        e.setExecutorType(s.getExecutorType());
        e.setMatchRegex(s.getMatchRegex());
        e.setReplacement(s.getReplacement());
        e.setKeepPrefix(s.getKeepPrefix());
        e.setKeepSuffix(s.getKeepSuffix());
        e.setMaskChar(s.getMaskChar());
        e.setReplaceValue(s.getReplaceValue());
        e.setHashLength(s.getHashLength());
        return e;
    }
}
