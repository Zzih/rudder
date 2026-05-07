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

package io.github.zzih.rudder.common.metrics;

/**
 * 全局 metric 命名常量。命名规范 {@code rudder_<module>_<subject>_<suffix>}:
 * <ul>
 *   <li>{@code _total} counter 累计值</li>
 *   <li>{@code _seconds} timer / histogram</li>
 * </ul>
 */
public final class MetricNames {

    private MetricNames() {
    }

    // ======================== AI ========================

    /** AI turn 启动计数。Tags: mode, workspace, status(DONE|CANCELLED|FAILED)。 */
    public static final String AI_TURN_TOTAL = "rudder_ai_turn_total";

    /** AI turn 耗时。Tags: mode。 */
    public static final String AI_TURN_DURATION_SECONDS = "rudder_ai_turn_duration_seconds";

    /** Provider 调用 token 数(streaming + non-streaming 共用)。Tags: type(prompt|completion), model。 */
    public static final String AI_TOKENS_TOTAL = "rudder_ai_tokens_total";

    /** Provider 调用成本(分)。Tags: provider, model, workspace。 */
    public static final String AI_COST_CENTS_TOTAL = "rudder_ai_cost_cents_total";

    /** Tool 调用计数。Tags: tool, source(NATIVE|SKILL|MCP), success。 */
    public static final String AI_TOOL_CALL_TOTAL = "rudder_ai_tool_call_total";

    /** Tool 调用耗时。Tags: tool。 */
    public static final String AI_TOOL_DURATION_SECONDS = "rudder_ai_tool_duration_seconds";

    /** Provider 异常。Tags: provider, reason。 */
    public static final String AI_PROVIDER_ERROR_TOTAL = "rudder_ai_provider_error_total";

    /** 取消次数。Tags: reason(user|timeout)。 */
    public static final String AI_CANCEL_TOTAL = "rudder_ai_cancel_total";
}
