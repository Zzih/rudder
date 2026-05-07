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

package io.github.zzih.rudder.service.workflow.controlflow.switch_;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.service.workflow.controlflow.AbstractControlFlowTask;
import io.github.zzih.rudder.service.workflow.controlflow.BranchDecision;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import lombok.extern.slf4j.Slf4j;

/**
 * SWITCH 控制流任务。
 * <p>
 * 按顺序评估每个分支的 JavaScript 条件表达式（支持变量池占位符替换），
 * 第一个匹配的分支被选中，其余分支跳过。无匹配时走默认分支。
 * <p>
 * 求值引擎与 DolphinScheduler 保持一致，使用 Nashorn Sandbox（JavaScript）。
 */
@Slf4j
public class SwitchTask extends AbstractControlFlowTask {

    private static final NashornSandbox SANDBOX;

    private static final long MAX_CPU_TIME_MS = 3_000;

    // @formatter:off
    private static final String POLYFILL_ARRAY_INCLUDES = """
            if (!Array.prototype.includes) {
                Object.defineProperty(Array.prototype, 'includes', {
                    value: function(valueToFind, fromIndex) {
                        if (this == null) throw new TypeError('"this" is null or not defined');
                        var o = Object(this), len = o.length >>> 0;
                        if (len === 0) return false;
                        var n = fromIndex | 0;
                        var k = Math.max(n >= 0 ? n : len - Math.abs(n), 0);
                        function sameValueZero(x, y) {
                            return x === y || (typeof x === 'number' && typeof y === 'number' && isNaN(x) && isNaN(y));
                        }
                        while (k < len) { if (sameValueZero(o[k], valueToFind)) return true; k++; }
                        return false;
                    }
                });
            }""";
    // @formatter:on

    /**
     * 变量占位符正则，与 DS 一致：匹配 ${varName} 及其可能的外围引号。
     * 例如 '${env}' 或 "${env}" 或 ${env} 都会被匹配并整体替换。
     */
    private static final Pattern VAR_PATTERN = Pattern.compile("['\"]?\\$\\{(.*?)\\}['\"]?");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    static {
        SANDBOX = NashornSandboxes.create();
        SANDBOX.setMaxCPUTime(MAX_CPU_TIME_MS);
        SANDBOX.setExecutor(Executors.newSingleThreadExecutor());
        try {
            SANDBOX.eval(POLYFILL_ARRAY_INCLUDES);
        } catch (ScriptException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Long nodeCode;
    private final String configJson;
    /** prop → value 视图,JS 表达式替换占位符用。SwitchTask 不关心 direct/type,只需 String 值。 */
    private final Map<String, String> varPool;

    public SwitchTask(Long nodeCode, String configJson, List<Property> varPoolSnapshot) {
        this.nodeCode = nodeCode;
        this.configJson = configJson;
        this.varPool = io.github.zzih.rudder.task.api.parser.PropertyIndex.byPropToValue(varPoolSnapshot);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle() throws TaskException {
        try {
            Map<String, Object> config = parseConfig(configJson);
            Map<String, Object> switchResult =
                    (Map<String, Object>) config.getOrDefault("switchResult", Map.of());
            List<Map<String, Object>> dependTaskList =
                    (List<Map<String, Object>>) switchResult.getOrDefault("dependTaskList", List.of());

            // 收集所有分支节点
            Set<Long> allBranchNodes = new HashSet<>();
            for (Map<String, Object> branch : dependTaskList) {
                Object nodeId = branch.get("nextNode");
                if (nodeId != null) {
                    allBranchNodes.add(Long.valueOf(nodeId.toString()));
                }
            }
            if (switchResult.get("nextNode") != null) {
                allBranchNodes.add(Long.valueOf(switchResult.get("nextNode").toString()));
            }

            log.info("[Switch] nodeCode={}, evaluating {} branch(es), defaultNode={}",
                    nodeCode, dependTaskList.size(), switchResult.get("nextNode"));

            // 按顺序评估条件
            Long nextNodeId = null;
            for (int i = 0; i < dependTaskList.size(); i++) {
                Map<String, Object> branch = dependTaskList.get(i);
                String condition = branch.getOrDefault("condition", "").toString();
                String resolved = resolveCondition(condition, varPool);
                boolean matched = evaluate(resolved);
                log.info("[Switch]   Branch {}: condition='{}', resolved='{}', matched={}, nextNode={}",
                        i, condition, resolved, matched, branch.get("nextNode"));
                if (matched) {
                    Object nodeId = branch.get("nextNode");
                    if (nodeId != null) {
                        nextNodeId = Long.valueOf(nodeId.toString());
                    }
                    break;
                }
            }
            // 无匹配 → 默认分支
            if (nextNodeId == null && switchResult.get("nextNode") != null) {
                nextNodeId = Long.valueOf(switchResult.get("nextNode").toString());
                log.info("[Switch]   No branch matched, using default nextNode={}", nextNodeId);
            }

            Set<Long> allowed = new HashSet<>();
            Set<Long> skipped = new HashSet<>(allBranchNodes);
            if (nextNodeId != null) {
                allowed.add(nextNodeId);
                skipped.remove(nextNodeId);
            }

            log.info("Switch evaluated: nodeCode={} next={} skipped={}", nodeCode, nextNodeId, skipped);
            setBranchDecision(new BranchDecision(allowed, skipped));
            status = TaskStatus.SUCCESS;

        } catch (Exception e) {
            status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_SWITCH_EVAL_FAILED, e.getMessage());
        }
    }

    /**
     * 替换条件表达式中的变量占位符，与 DS 的 generateContentWithTaskParams 逻辑一致：
     * <ol>
     *   <li>单引号全部替换为双引号（JS 兼容）</li>
     *   <li>${varName} 及其外围引号整体替换为实际值：数字/布尔不加引号，字符串加双引号</li>
     * </ol>
     */
    static String resolveCondition(String condition, Map<String, String> params) {
        if (condition == null || condition.isEmpty()) {
            return condition;
        }
        // 与 DS 一致：单引号 → 双引号
        String content = condition.replace('\'', '"');

        if (params == null || params.isEmpty()) {
            return content;
        }

        Matcher m = VAR_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String paramName = m.group(1);
            String paramValue = params.get(paramName);
            if (paramValue == null) {
                // 未找到变量，保留原文
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String replacement;
            if (NUMERIC_PATTERN.matcher(paramValue).matches()
                    || "true".equalsIgnoreCase(paramValue)
                    || "false".equalsIgnoreCase(paramValue)) {
                replacement = paramValue;
            } else {
                replacement = "\"" + paramValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 使用 Nashorn Sandbox 评估 JavaScript 表达式，与 DS 保持一致。
     */
    private static boolean evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String trimmed = expression.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        try {
            Object result = SANDBOX.eval(trimmed);
            return Boolean.TRUE.equals(result);
        } catch (ScriptException e) {
            log.warn("JS evaluation failed for expression [{}]: {}", trimmed, e.getMessage());
            return false;
        }
    }
}
