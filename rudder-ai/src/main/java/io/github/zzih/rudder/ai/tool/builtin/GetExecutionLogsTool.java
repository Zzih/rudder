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

package io.github.zzih.rudder.ai.tool.builtin;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.TaskInstanceService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** 读取脚本执行日志(调试错误场景用)。 */
@Component
@RequiredArgsConstructor
public class GetExecutionLogsTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "executionId":{"type":"integer"},
                        "offsetLine":{"type":"integer","default":0}
                      },
                      "required":["executionId"]
                    }""");

    private final TaskInstanceService taskInstanceService;

    @Override
    public String name() {
        return "get_execution_logs";
    }

    @Override
    public String description() {
        return "Read the log of a past script execution. "
                + "Use this to diagnose FAILED executions and propose fixes.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long id = input.get("executionId").asLong();
        int offset = input.hasNonNull("offsetLine") ? input.get("offsetLine").asInt() : 0;
        var log = taskInstanceService.getLog(id, offset);
        return JsonUtils.toJson(log);
    }
}
