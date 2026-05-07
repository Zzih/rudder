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

package io.github.zzih.rudder.ai.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 按 {@link ExpectedSpec} 对 {@link OneshotResult} 做多维度校验。每一条失败原因**独立累计**,
 * UI 上能一次看到所有问题,不是命中第一条就 early-return。
 */
@Component
public class EvalVerifier {

    public Verdict verify(OneshotResult result, ExpectedSpec spec) {
        List<String> failures = new ArrayList<>();

        if (!result.executionSucceeded()) {
            failures.add("execution error: " + result.getError());
            return new Verdict(false, failures);
        }

        String text = result.getFinalText() == null ? "" : result.getFinalText();
        ExpectedSpec safeSpec = spec == null ? new ExpectedSpec() : spec;

        verifyText(text, safeSpec, failures);
        verifyTools(result, safeSpec, failures);
        verifyPerformance(result, safeSpec, failures);

        return new Verdict(failures.isEmpty(), failures);
    }

    private void verifyText(String text, ExpectedSpec spec, List<String> failures) {
        if (spec.getSqlPattern() != null && !spec.getSqlPattern().isBlank()) {
            try {
                Pattern p = Pattern.compile(spec.getSqlPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                if (!p.matcher(text).find()) {
                    failures.add("sqlPattern mismatch: regex `" + spec.getSqlPattern() + "` did not match output");
                }
            } catch (PatternSyntaxException ex) {
                failures.add("sqlPattern invalid regex: " + ex.getMessage());
            }
        }
        String lower = text.toLowerCase();
        if (spec.getMustContain() != null) {
            for (String kw : spec.getMustContain()) {
                if (kw != null && !kw.isEmpty() && !lower.contains(kw.toLowerCase())) {
                    failures.add("mustContain missing: " + kw);
                }
            }
        }
        if (spec.getMustNotContain() != null) {
            for (String kw : spec.getMustNotContain()) {
                if (kw != null && !kw.isEmpty() && lower.contains(kw.toLowerCase())) {
                    failures.add("mustNotContain violated: " + kw);
                }
            }
        }
    }

    private void verifyTools(OneshotResult result, ExpectedSpec spec, List<String> failures) {
        List<OneshotResult.ToolInvocation> calls = result.getToolCalls() == null
                ? List.of()
                : result.getToolCalls();
        Set<String> calledNames = new HashSet<>();
        for (OneshotResult.ToolInvocation c : calls) {
            calledNames.add(c.getName());
        }

        if (spec.getMustCallTools() != null) {
            for (String t : spec.getMustCallTools()) {
                if (t != null && !t.isBlank() && !calledNames.contains(t)) {
                    failures.add("mustCallTools missing: " + t);
                }
            }
        }
        if (spec.getMustNotCallTools() != null) {
            for (String t : spec.getMustNotCallTools()) {
                if (t != null && !t.isBlank() && calledNames.contains(t)) {
                    failures.add("mustNotCallTools violated: " + t);
                }
            }
        }
        if (spec.getMinToolCalls() != null && calls.size() < spec.getMinToolCalls()) {
            failures.add("minToolCalls not met: expected >= " + spec.getMinToolCalls()
                    + ", actual " + calls.size());
        }
        if (spec.getMaxToolCalls() != null && calls.size() > spec.getMaxToolCalls()) {
            failures.add("maxToolCalls exceeded: expected <= " + spec.getMaxToolCalls()
                    + ", actual " + calls.size());
        }
    }

    private void verifyPerformance(OneshotResult result, ExpectedSpec spec, List<String> failures) {
        if (spec.getMaxLatencyMs() != null && result.getLatencyMs() != null
                && result.getLatencyMs() > spec.getMaxLatencyMs()) {
            failures.add("maxLatencyMs exceeded: expected <= " + spec.getMaxLatencyMs()
                    + "ms, actual " + result.getLatencyMs() + "ms");
        }
        if (spec.getMaxTokens() != null && result.totalTokens() > spec.getMaxTokens()) {
            failures.add("maxTokens exceeded: expected <= " + spec.getMaxTokens()
                    + ", actual " + result.totalTokens());
        }
    }

    @Data
    @AllArgsConstructor
    public static class Verdict {

        private boolean passed;
        private List<String> failures;
    }
}
