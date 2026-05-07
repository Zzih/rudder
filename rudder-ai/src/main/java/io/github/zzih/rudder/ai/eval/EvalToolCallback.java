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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.llm.api.tool.AgentTool;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * eval 专用 ToolCallback。和生产 {@code RudderToolCallback} 的差异:
 * <ul>
 *   <li>不写 {@code t_r_ai_message} (不污染对话历史表)</li>
 *   <li>不走权限 / 审批门禁(eval 要尽可能跑通,能重现生产行为即可)</li>
 *   <li>每次调用按顺序追加到 {@code trace} 列表,供 {@link EvalVerifier} 做序列断言</li>
 * </ul>
 *
 * <p>如果未来需要测权限/审批,在 spec 里单独加断言字段,而不是让 eval 自己触发审批。
 */
@Slf4j
public class EvalToolCallback implements ToolCallback {

    private final AgentTool tool;
    private final ToolExecutionContext ctx;
    private final List<OneshotResult.ToolInvocation> trace;

    public EvalToolCallback(AgentTool tool, ToolExecutionContext ctx,
                            List<OneshotResult.ToolInvocation> trace) {
        this.tool = tool;
        this.ctx = ctx;
        this.trace = trace;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        JsonNode schema = tool.inputSchema();
        return DefaultToolDefinition.builder()
                .name(tool.name())
                .description(tool.description() == null ? "" : tool.description())
                .inputSchema(schema == null ? "{}" : schema.toString())
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = parseInput(toolInput);
        long start = System.currentTimeMillis();
        String output;
        boolean success;
        String errorMessage = null;
        try {
            output = tool.execute(input, ctx);
            success = true;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            output = "Error: " + errorMessage;
            log.debug("eval tool {} failed: {}", tool.name(), errorMessage);
        }
        int latency = (int) (System.currentTimeMillis() - start);
        trace.add(OneshotResult.ToolInvocation.builder()
                .name(tool.name())
                .input(input)
                .output(output)
                .success(success)
                .errorMessage(errorMessage)
                .latencyMs(latency)
                .build());
        return output;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return call(toolInput);
    }

    private JsonNode parseInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return JsonUtils.createObjectNode();
        }
        try {
            return JsonUtils.parseTree(raw);
        } catch (Exception e) {
            return JsonUtils.createObjectNode();
        }
    }
}
